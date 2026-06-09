package com.financialapigateway.accountservice.dto;

import com.financialapigateway.accountservice.enumeration.AccountType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {
    private UUID userId;
    private AccountType accountType;
}
