package com.financialapigateway.transactionservice.repository;

import com.financialapigateway.transactionservice.entity.Transaction;
import com.financialapigateway.transactionservice.enumeration.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findBySenderIdOrRecipientId(String senderId, String recipientId);

    @Query("SELECT t FROM Transaction t WHERE " +
    "(t.senderId = :accountNumber OR t.recipientId = :accountNumber)" +
    "AND t.status = :status")
    List<Transaction> findByAccountNumberAndStatus(@Param("accountNumber") String accountNumber,
                                                   @Param("status") Status status);
}
