package com.financialapigateway.transactionservice.event;

import com.financialapigateway.transactionservice.enumeration.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private UUID transactionId;
    private String senderId;
    private String recipientId;
    private BigDecimal amount;
    private Status status;
    private LocalDateTime createdAt;
}
