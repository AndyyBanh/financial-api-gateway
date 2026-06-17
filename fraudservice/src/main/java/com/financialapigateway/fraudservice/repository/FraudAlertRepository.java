package com.financialapigateway.fraudservice.repository;

import com.financialapigateway.fraudservice.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    Optional<FraudAlert> findByTransactionId(UUID transactionId);
    Boolean existsByTransactionId(UUID transactionId);
}
