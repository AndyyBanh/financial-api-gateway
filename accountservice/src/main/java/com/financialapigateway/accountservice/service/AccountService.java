package com.financialapigateway.accountservice.service;

import com.financialapigateway.accountservice.dto.AccountDto;
import com.financialapigateway.accountservice.entity.Account;
import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionEvent;
import com.financialapigateway.accountservice.event.TransactionResultEvent;
import com.financialapigateway.accountservice.exceptions.AccountNotFoundException;
import com.financialapigateway.accountservice.kafka.TransactionResultProducer;
import com.financialapigateway.accountservice.repository.AccountRepository;
import com.financialapigateway.accountservice.response.AccountResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    private final TransactionResultProducer transactionResultProducer;

    @Autowired
    public AccountService(AccountRepository accountRepository, TransactionResultProducer transactionResultProducer) {
        this.accountRepository = accountRepository;
        this.transactionResultProducer = transactionResultProducer;
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

    // Don't handle exceptions because Kafka will keep infinite retry
    public void processTransaction(TransactionEvent event) {
        Optional<Account> senderAcc = this.accountRepository.findByAccountNumber(event.getSenderId());
        Optional<Account> receiverAcc = this.accountRepository.findByAccountNumber(event.getRecipientId());

        if (senderAcc.isEmpty() || receiverAcc.isEmpty()) {
            TransactionResultEvent transactionResultEvent = mapToEvent(event, Status.FAILED);
            this.transactionResultProducer.send(transactionResultEvent);
            return;
        }

        Account sender = senderAcc.get();
        Account receiver = receiverAcc.get();

        if (sender.getBalance() < event.getAmount()) {
            TransactionResultEvent transactionResultEvent = mapToEvent(event, Status.FAILED);
            this.transactionResultProducer.send(transactionResultEvent);
            return;
        }

        sender.setBalance(sender.getBalance() - event.getAmount());
        receiver.setBalance(receiver.getBalance() + event.getAmount());
        this.accountRepository.save(sender);
        this.accountRepository.save(receiver);

        TransactionResultEvent transactionResultEvent = mapToEvent(event, Status.COMPLETE);
        this.transactionResultProducer.send(transactionResultEvent);
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

    private TransactionResultEvent mapToEvent(TransactionEvent event, Status status) {
        TransactionResultEvent transactionResultEvent = new TransactionResultEvent();
        transactionResultEvent.setTransactionId(event.getTransactionId());
        transactionResultEvent.setStatus(status);
        return transactionResultEvent;
    }
}
