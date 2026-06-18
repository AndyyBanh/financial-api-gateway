package com.financialapigateway.transactionservice.service;

import com.financialapigateway.transactionservice.dto.TransactionDto;
import com.financialapigateway.transactionservice.entity.Transaction;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.event.TransactionEvent;
import com.financialapigateway.transactionservice.event.TransactionResultEvent;
import com.financialapigateway.transactionservice.exceptions.AccountNotFoundException;
import com.financialapigateway.transactionservice.exceptions.InsufficientBalanceException;
import com.financialapigateway.transactionservice.exceptions.TransactionNotFoundException;
import com.financialapigateway.transactionservice.feignclient.AccountClient;
import com.financialapigateway.transactionservice.kafka.TransactionProducer;
import com.financialapigateway.transactionservice.repository.TransactionRepository;
import com.financialapigateway.transactionservice.response.AccountResponse;
import com.financialapigateway.transactionservice.response.TransactionResponse;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionProducer transactionProducer;
    private final AccountClient accountClient;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository,  TransactionProducer transactionProducer, AccountClient accountClient) {
        this.transactionRepository = transactionRepository;
        this.transactionProducer = transactionProducer;
        this.accountClient = accountClient;
    }

    public TransactionResponse createTransaction(TransactionDto input) {
        AccountResponse sender;
        AccountResponse receiver;
        try {
            sender = this.accountClient.getAccountByAccountNumber(input.getSenderId());
            receiver = this.accountClient.getAccountByAccountNumber(input.getRecipientId());
        } catch (FeignException.NotFound ex) {
            throw new AccountNotFoundException("Account not found");
        }
        if (sender.getBalance() < input.getAmount()) {
            throw new InsufficientBalanceException("Sender account balance is insufficient");
        }

        Transaction transaction = mapToEntity(input);
        this.transactionRepository.save(transaction);
        TransactionEvent transactionEvent = mapToEvent(transaction);
        this.transactionProducer.send(transactionEvent);
        return mapToResponse(transaction);
    }

    public TransactionResponse getTransactionById(UUID transactionId) {
        Transaction transaction = this.transactionRepository.findById(transactionId).orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));
        return mapToResponse(transaction);
    }

    public List<TransactionResponse> getAllTransactionsByAccountNumber(String accountNumber, Status status) {
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

    // Don't handle exception in consumers will cause infinite retry
    public void updateTransactionStatus(TransactionResultEvent transactionResultEvent) {
        Optional<Transaction> transactionOptional = this.transactionRepository.findById(transactionResultEvent.getTransactionId());
        if (transactionOptional.isPresent()) {
            Transaction transaction = transactionOptional.get();
            transaction.setStatus(transactionResultEvent.getStatus());
            this.transactionRepository.save(transaction);
            System.out.println("Transaction successfully updated: " + transactionResultEvent.getTransactionId() + " " + transactionResultEvent.getStatus());
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

    private TransactionEvent mapToEvent(Transaction input) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(input.getTransactionId());
        event.setAmount(input.getAmount());
        event.setSenderId(input.getSenderId());
        event.setRecipientId(input.getRecipientId());
        event.setCreatedAt(input.getCreatedAt());
        event.setStatus(input.getStatus());
        return event;
    }
}
