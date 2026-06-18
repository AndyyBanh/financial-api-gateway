package com.financialapigateway.fraudservice.response;

import com.financialapigateway.fraudservice.enumeration.AccountType;
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
