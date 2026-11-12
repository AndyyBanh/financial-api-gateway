package com.financialapigateway.accountservice.entity;

import com.financialapigateway.accountservice.enumeration.AccountType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "accounts", indexes = {
        @Index(name = "index_accounts_account_number", columnList = "accountNumber"),
        @Index(name = "index_accounts_user_id", columnList = "userId")
})
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    // accountId
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
