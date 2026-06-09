package com.financialapigateway.accountservice.response;

import com.financialapigateway.accountservice.enumeration.AccountType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID userId;
    private UUID accountId;
    private String accountNumber;
    private float balance;
    private AccountType accountType;
}
