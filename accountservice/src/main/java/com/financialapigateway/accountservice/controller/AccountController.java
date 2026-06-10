package com.financialapigateway.accountservice.controller;

import com.financialapigateway.accountservice.dto.AccountDto;
import com.financialapigateway.accountservice.response.AccountResponse;
import com.financialapigateway.accountservice.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountDto input) {
        return ResponseEntity.ok(this.accountService.createAccount(input));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountByAccountId(@PathVariable UUID accountId) {
        return ResponseEntity.ok(this.accountService.getAccountById(accountId));
    }

    @GetMapping(params = "accountNumber")
    public ResponseEntity<AccountResponse> getAccountByAccountNumber(@RequestParam String accountNumber) {
        return ResponseEntity.ok(this.accountService.getAccountByAccountNumber(accountNumber));
    }

    @GetMapping(params = "userId")
    public ResponseEntity<List<AccountResponse>> getAccountsByUserId(@RequestParam UUID userId) {
        return ResponseEntity.ok(this.accountService.getAllAccountsByUserId(userId));
    }
}
