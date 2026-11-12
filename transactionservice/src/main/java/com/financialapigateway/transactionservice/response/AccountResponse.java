package com.financialapigateway.transactionservice.response;

import com.financialapigateway.transactionservice.enumeration.AccountType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID userId;
    private UUID accountId;
    private String accountNumber;
    private BigDecimal balance;
    private AccountType accountType;
}
