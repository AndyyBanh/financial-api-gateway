package com.financialapigateway.accountservice.service;

import com.financialapigateway.accountservice.dto.AccountDto;
import com.financialapigateway.accountservice.entity.Account;
import com.financialapigateway.accountservice.entity.ProcessedTransaction;
import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionEvent;
import com.financialapigateway.accountservice.exceptions.AccountNotFoundException;
import com.financialapigateway.accountservice.repository.AccountRepository;
import com.financialapigateway.accountservice.repository.ProcessedTransactionRepository;
import com.financialapigateway.accountservice.response.AccountResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    private final ProcessedTransactionRepository processedTransactionRepository;

    @Autowired
    public AccountService(AccountRepository accountRepository, ProcessedTransactionRepository processedTransactionRepository) {
        this.accountRepository = accountRepository;
        this.processedTransactionRepository = processedTransactionRepository;
    }

    public AccountResponse createAccount(AccountDto input) {
        log.debug("Creating account userId={}", input.getUserId());
        Account account = mapToEntity(input);
        this.accountRepository.save(account);
        return mapToResponse(account);
    }

    public AccountResponse getAccountById(UUID accountId) {
        log.debug("Getting account accountId={}", accountId);
        Account account = this.accountRepository.findById(accountId).orElseThrow(() -> {
            log.warn("Account not found accountId={}", accountId);
            return new AccountNotFoundException("account not found");
        });
        return mapToResponse(account);
    }

    public AccountResponse getAccountByAccountNumber(String accountNumber) {
        log.debug("Getting account by account number");
        Account account = this.accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> {
            log.warn("Account not found by account number");
            return new AccountNotFoundException("account not found");
        });
        return mapToResponse(account);
    }

    public List<AccountResponse> getAllAccountsByUserId(UUID userId) {
        log.debug("Getting accounts userId={}", userId);
        List<Account> accounts = this.accountRepository.findByUserId(userId);

        if (accounts.isEmpty()) {
            log.warn("No accounts found userId={}", userId);
            throw new AccountNotFoundException("account not found");
        }

        return accounts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Moves money for a transaction, at most once per transactionId.
     * <p>
     * Errors are left to the Kafka error handler. A repeated event returns the saved result.
     *
     * @param event
     * @return COMPLETE, or FAILED if an account is missing or the balance is too low
     */
    @Transactional
    public Status processTransaction(TransactionEvent event) {
        log.debug("Processing transaction transactionId={}", event.getTransactionId());
        Optional<ProcessedTransaction> processed = this.processedTransactionRepository.findById(event.getTransactionId());
        if (processed.isPresent()) {
            log.debug("Transaction already processed transactionId={}", event.getTransactionId());
            return processed.get().getStatus();
        }

        Status status = applyTransfer(event);
        this.processedTransactionRepository.save(new ProcessedTransaction(event.getTransactionId(), status));
        log.info("Transaction processed transactionId={} status={}", event.getTransactionId(), status);
        return status;
    }

    private Status applyTransfer(TransactionEvent event) {
        log.debug("Applying transfer transactionId={}", event.getTransactionId());
        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            log.warn("Invalid amount for transactionId={}", event.getTransactionId());
            return Status.FAILED;
        }
        Optional<Account> senderAcc = this.accountRepository.findByAccountNumber(event.getSenderId());
        Optional<Account> receiverAcc = this.accountRepository.findByAccountNumber(event.getRecipientId());

        if (senderAcc.isEmpty() || receiverAcc.isEmpty()) {
            log.warn("Account not found for transactionId={}", event.getTransactionId());
            return Status.FAILED;
        }

        Account sender = senderAcc.get();
        Account receiver = receiverAcc.get();

        if (sender.getBalance().compareTo(event.getAmount()) < 0) {
            log.warn("Insufficient balance for transactionId={}", event.getTransactionId());
            return Status.FAILED;
        }

        sender.setBalance(sender.getBalance().subtract(event.getAmount()));
        receiver.setBalance(receiver.getBalance().add(event.getAmount()));
        this.accountRepository.save(sender);
        this.accountRepository.save(receiver);
        return Status.COMPLETE;
    }


    private Account mapToEntity(AccountDto accountDto) {
        Account account = new Account();
        account.setUserId(accountDto.getUserId());
        account.setAccountType(accountDto.getAccountType());
        account.setBalance(BigDecimal.ZERO);
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
