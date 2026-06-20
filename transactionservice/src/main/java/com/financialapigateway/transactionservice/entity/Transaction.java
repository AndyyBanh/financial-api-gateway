package com.financialapigateway.transactionservice.entity;

import com.financialapigateway.transactionservice.enumeration.Status;
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
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_sender_id", columnList = "senderId"),
        @Index(name = "idx_transactions_recipient_id", columnList = "recipientId")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID transactionId;

    // references accountNumber String in Account Service
    @Column(nullable = false)
    private String senderId;
    @Column(nullable = false)
    private String recipientId;

    @Column(nullable = false)
    private float amount;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
