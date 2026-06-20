package com.financialapigateway.fraudservice.entity;

import com.financialapigateway.fraudservice.enumeration.Reason;
import com.financialapigateway.fraudservice.enumeration.Severity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fraud_alerts", indexes = {
        @Index(name = "idx_fraud_alerts_transaction_id", columnList = "transactionId")
})
public class FraudAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID alertId;

    @Column(nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Reason reason;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
