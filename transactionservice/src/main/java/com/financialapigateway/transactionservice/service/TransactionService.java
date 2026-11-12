package com.financialapigateway.transactionservice.service;

import com.financialapigateway.transactionservice.dto.TransactionDto;
import com.financialapigateway.transactionservice.entity.OutboxEvent;
import com.financialapigateway.transactionservice.entity.Transaction;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.event.TransactionResultEvent;
import com.financialapigateway.transactionservice.exceptions.AccountNotFoundException;
import com.financialapigateway.transactionservice.exceptions.InsufficientBalanceException;
import com.financialapigateway.transactionservice.exceptions.InvalidAmountException;
import com.financialapigateway.transactionservice.exceptions.TransactionNotFoundException;
import com.financialapigateway.transactionservice.feignclient.AccountClient;
import com.financialapigateway.transactionservice.repository.OutboxEventRepository;
import com.financialapigateway.transactionservice.repository.TransactionRepository;
import com.financialapigateway.transactionservice.response.AccountResponse;
import com.financialapigateway.transactionservice.response.TransactionResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AccountClient accountClient;
    private final TransactionTemplate transactionTemplate;
    private final Executor accountLookupExecutor;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository, OutboxEventRepository outboxEventRepository, AccountClient accountClient,
                              TransactionTemplate transactionTemplate, @Qualifier("accountLookupExecutor") Executor accountLookupExecutor) {
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.accountClient = accountClient;
        this.transactionTemplate = transactionTemplate;
        this.accountLookupExecutor = accountLookupExecutor;
    }

    /**
     * Rejects amounts that are missing, zero or negative, have more than 2 decimal places,
     * or are too large for the NUMERIC(19,2) column.
     *
     * @param amount the requested transfer amount
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            log.warn("Rejected new transaction: amount is missing");
            throw new InvalidAmountException("amount is required");
        }
        if (amount.signum() <= 0) {
            log.warn("Rejected new transaction: amount is not greater than 0");
            throw new InvalidAmountException("amount must be greater than 0");
        }
        // stripTrailingZeros so 50.10 or 50.100 count as 2 decimal places, but 0.005 doesn't
        if (amount.stripTrailingZeros().scale() > 2) {
            log.warn("Rejected new transaction: amount has more than 2 decimal places");
            throw new InvalidAmountException("amount must have at most 2 decimal places");
        }
        // digits before the decimal point must fit NUMERIC(19,2), which allows 17
        if (amount.precision() - amount.scale() > 17) {
            log.warn("Rejected new transaction: amount is too large");
            throw new InvalidAmountException("amount is too large");
        }
    }

    /**
     * Validates and saves a new PENDING transaction.
     * <p>
     * The transaction and its outbox row are saved in one DB transaction, and OutboxRelay sends the
     * event to Kafka. The account lookups run before that DB transaction starts, so no DB connection
     * is held while waiting on the Account Service.
     *
     * @param input
     * @return the saved transaction, still PENDING
     */
    public TransactionResponse createTransaction(TransactionDto input) {
        log.debug("Creating transaction");
        validateAmount(input.getAmount());
        AccountResponse sender;
        AccountResponse receiver;
        // Recipient lookup runs on another thread while this thread looks up the sender
        CompletableFuture<AccountResponse> receiverLookup = CompletableFuture.supplyAsync(
                () -> this.accountClient.getAccountByAccountNumber(input.getRecipientId()), this.accountLookupExecutor);
        try {
            sender = this.accountClient.getAccountByAccountNumber(input.getSenderId());
            receiver = joinLookup(receiverLookup);
        } catch (FeignException.NotFound ex) {
            log.warn("Sender or recipient account not found for new transaction");
            throw new AccountNotFoundException("Account not found");
        }
        if (sender.getBalance().compareTo(input.getAmount()) < 0) {
            log.warn("Insufficient balance for new transaction senderAccountId={}", sender.getAccountId());
            throw new InsufficientBalanceException("Sender account balance is insufficient");
        }

        Transaction transaction = mapToEntity(input);
        this.transactionTemplate.executeWithoutResult(status -> {
            this.transactionRepository.save(transaction);
            this.outboxEventRepository.save(mapToOutboxEvent(transaction));
        });
        return mapToResponse(transaction);
    }

    public TransactionResponse getTransactionById(UUID transactionId) {
        log.debug("Getting transaction transactionId={}", transactionId);
        Transaction transaction = this.transactionRepository.findById(transactionId).orElseThrow(() -> {
            log.warn("Transaction not found transactionId={}", transactionId);
            return new TransactionNotFoundException("Transaction not found");
        });
        return mapToResponse(transaction);
    }

    public List<TransactionResponse> getAllTransactionsByAccountNumber(String accountNumber, Status status) {
        log.debug("Getting transactions by account number status={}", status);
        List<Transaction> transactions;

        if (status == null) {
            transactions = this.transactionRepository.findBySenderIdOrRecipientId(accountNumber, accountNumber);
        } else {
            transactions = this.transactionRepository.findByAccountNumberAndStatus(accountNumber, status);
        }
        return transactions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Counts the transactions an account sent after the given time.
     * <p>
     * Used by the Fraud Service's frequency rule, so it gets one number instead of the full history.
     *
     * @param senderId account number of the sender
     * @param since only transactions created after this time are counted
     * @return number of matching transactions
     */
    public long countSentTransactionsSince(String senderId, LocalDateTime since) {
        log.debug("Counting sent transactions since={}", since);
        return this.transactionRepository.countBySenderIdAndCreatedAtAfter(senderId, since);
    }

    /**
     * Sets a PENDING transaction to its final status.
     * <p>
     * Ignores results for unknown or already-final transactions. Errors are left to the Kafka
     * error handler, which retries and then sends the record to the DLT.
     *
     * @param transactionResultEvent
     */
    public void updateTransactionStatus(TransactionResultEvent transactionResultEvent) {
        log.debug("Updating transaction status transactionId={} status={}", transactionResultEvent.getTransactionId(), transactionResultEvent.getStatus());
        Optional<Transaction> transactionOptional = this.transactionRepository.findById(transactionResultEvent.getTransactionId());
        if (transactionOptional.isPresent()) {
            Transaction transaction = transactionOptional.get();
            // Only PENDING can move to a final state, so a late or duplicate result can't overwrite it
            if (transaction.getStatus() != Status.PENDING) {
                log.warn("Ignoring status update for finalized transactionId={} {} -> {}", transactionResultEvent.getTransactionId(), transaction.getStatus(), transactionResultEvent.getStatus());
                return;
            }
            transaction.setStatus(transactionResultEvent.getStatus());
            this.transactionRepository.save(transaction);
            log.info("Transaction updated transactionId={} status={}", transactionResultEvent.getTransactionId(), transactionResultEvent.getStatus());
        } else {
            log.warn("Transaction not found for status update transactionId={}", transactionResultEvent.getTransactionId());
        }
    }

    // Waits for an async lookup and rethrows its original exception (e.g. FeignException.NotFound)
    private AccountResponse joinLookup(CompletableFuture<AccountResponse> lookup) {
        try {
            return lookup.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw ex;
        }
    }

    private Transaction mapToEntity(TransactionDto input) {
        Transaction transaction = new Transaction();
        transaction.setSenderId(input.getSenderId());
        transaction.setRecipientId(input.getRecipientId());
        transaction.setAmount(input.getAmount());
        transaction.setStatus(Status.PENDING);
        transaction.setCreatedAt(LocalDateTime.now());
        return transaction;
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(transaction.getTransactionId());
        response.setStatus(transaction.getStatus());
        response.setAmount(transaction.getAmount());
        response.setSender(transaction.getSenderId());
        response.setRecipient(transaction.getRecipientId());
        response.setCreationDate(transaction.getCreatedAt());
        return response;
    }

    private OutboxEvent mapToOutboxEvent(Transaction input) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTransactionId(input.getTransactionId());
        outboxEvent.setAmount(input.getAmount());
        outboxEvent.setSenderId(input.getSenderId());
        outboxEvent.setRecipientId(input.getRecipientId());
        outboxEvent.setCreatedAt(input.getCreatedAt());
        outboxEvent.setStatus(input.getStatus());
        return outboxEvent;
    }
}
