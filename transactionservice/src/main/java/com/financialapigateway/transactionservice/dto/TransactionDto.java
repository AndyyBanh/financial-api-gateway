package com.financialapigateway.transactionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {
    private String senderId;
    private String recipientId;
    private BigDecimal amount;
}
