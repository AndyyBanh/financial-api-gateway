package com.financialapigateway.accountservice.entity;

import com.financialapigateway.accountservice.enumeration.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// A transaction result waiting to be sent to Kafka. Saved in the same DB transaction as the transfer,
// and deleted by OutboxRelay once Kafka confirms the send.
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_events_created_at", columnList = "createdAt")
})
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
