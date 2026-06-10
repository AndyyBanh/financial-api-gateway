package com.financialapigateway.accountservice.service;

import com.financialapigateway.accountservice.dto.AccountDto;
import com.financialapigateway.accountservice.entity.Account;
import com.financialapigateway.accountservice.exceptions.AccountNotFoundException;
import com.financialapigateway.accountservice.repository.AccountRepository;
import com.financialapigateway.accountservice.response.AccountResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse createAccount(AccountDto input) {
        Account account = mapToEntity(input);
        this.accountRepository.save(account);
        return mapToResponse(account);
    }

    public AccountResponse getAccountById(UUID accountId) {
        Account account = this.accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException("account not found"));
        return mapToResponse(account);
    }

    public AccountResponse getAccountByAccountNumber(String accountNumber) {
        Account account = this.accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new AccountNotFoundException("account not found"));
        return mapToResponse(account);
    }

    public List<AccountResponse> getAllAccountsByUserId(UUID userId) {
        List<Account> accounts = this.accountRepository.findByUserId(userId);

        if (accounts.isEmpty()) {
            throw new AccountNotFoundException("account not found");
        }

        return accounts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Account mapToEntity(AccountDto accountDto) {
        Account account = new Account();
        account.setUserId(accountDto.getUserId());
        account.setAccountType(accountDto.getAccountType());
        account.setBalance(0.00F);
        account.setAccountNumber(generateAccountNumber());
        account.setCreatedAt(LocalDateTime.now());
        return account;
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setUserId(account.getUserId());
        accountResponse.setAccountId(account.getId());
        accountResponse.setAccountNumber(account.getAccountNumber());
        accountResponse.setBalance(account.getBalance());
        accountResponse.setAccountType(account.getAccountType());
        return accountResponse;
    }

    private String generateAccountNumber() {
        return String.format("%010d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }
}
