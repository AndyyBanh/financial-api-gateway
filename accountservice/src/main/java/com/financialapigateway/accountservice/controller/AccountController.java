package com.financialapigateway.accountservice.controller;

import com.financialapigateway.accountservice.dto.AccountDto;
import com.financialapigateway.accountservice.response.AccountResponse;
import com.financialapigateway.accountservice.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
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
        log.debug("Received create account request userId={}", input.getUserId());
        return ResponseEntity.ok(this.accountService.createAccount(input));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountByAccountId(@PathVariable UUID accountId) {
        log.debug("Received get account request accountId={}", accountId);
        return ResponseEntity.ok(this.accountService.getAccountById(accountId));
    }

    @GetMapping(params = "accountNumber")
    public ResponseEntity<AccountResponse> getAccountByAccountNumber(@RequestParam String accountNumber) {
        log.debug("Received get account by account number request");
        return ResponseEntity.ok(this.accountService.getAccountByAccountNumber(accountNumber));
    }

    @GetMapping(params = "userId")
    public ResponseEntity<List<AccountResponse>> getAccountsByUserId(@RequestParam UUID userId) {
        log.debug("Received get accounts request userId={}", userId);
        return ResponseEntity.ok(this.accountService.getAllAccountsByUserId(userId));
    }
}
