package com.financialapigateway.accountservice.entity;

import com.financialapigateway.accountservice.enumeration.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Saves each transaction's result so a repeated event doesn't move money twice.
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "processed_transactions")
public class ProcessedTransaction {
    @Id
    private UUID transactionId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
}
