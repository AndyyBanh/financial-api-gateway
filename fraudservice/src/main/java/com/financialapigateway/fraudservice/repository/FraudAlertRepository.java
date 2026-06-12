package com.financialapigateway.fraudservice.repository;

import com.financialapigateway.fraudservice.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    Optional<FraudAlert> findByTransactionId(UUID transactionId);
}
