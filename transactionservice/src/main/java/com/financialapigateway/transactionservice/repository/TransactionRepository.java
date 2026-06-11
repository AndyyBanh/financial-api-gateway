package com.financialapigateway.transactionservice.repository;

import com.financialapigateway.transactionservice.entity.Transaction;
import com.financialapigateway.transactionservice.enumeration.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findBySenderIdOrRecipientId(String senderId, String recipientId);
    List<Transaction> findAllBySenderIdOrRecipientIdAndStatus(String senderId, String recipientId, Status status);
}
