package com.financialapigateway.transactionservice.response;

import com.financialapigateway.transactionservice.enumeration.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID transactionId;
    private Status status;
    private String sender;
    private String recipient;
    private float amount;
    private LocalDateTime creationDate;
}
