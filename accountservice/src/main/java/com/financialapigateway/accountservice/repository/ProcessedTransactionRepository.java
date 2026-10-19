package com.financialapigateway.accountservice.repository;

import com.financialapigateway.accountservice.entity.ProcessedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransaction, UUID> {
}
