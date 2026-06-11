package com.financialapigateway.transactionservice.service;

import com.financialapigateway.transactionservice.dto.TransactionDto;
import com.financialapigateway.transactionservice.entity.Transaction;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.exceptions.TransactionNotFoundException;
import com.financialapigateway.transactionservice.repository.TransactionRepository;
import com.financialapigateway.transactionservice.response.TransactionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // exception handled in mapToEntity method
    public TransactionResponse createTransaction(TransactionDto input) {
        Transaction transaction = mapToEntity(input);
        this.transactionRepository.save(transaction);
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
            transactions = this.transactionRepository.findAllBySenderIdOrRecipientIdAndStatus(
                    accountNumber, accountNumber, status);
        }
        return transactions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    private Transaction mapToEntity(TransactionDto input) {
        Transaction transaction = new Transaction();
        transaction.setSenderId(input.getSenderId());
        transaction.setRecipientId(input.getRecipientId());
        // need to add validation logic i.e check if sender balance is sufficient else throw exception here
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

}
