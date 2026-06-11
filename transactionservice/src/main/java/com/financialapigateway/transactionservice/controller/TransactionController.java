package com.financialapigateway.transactionservice.controller;

import com.financialapigateway.transactionservice.dto.TransactionDto;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.response.TransactionResponse;
import com.financialapigateway.transactionservice.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @Autowired
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@RequestBody TransactionDto input) {
        return ResponseEntity.ok(this.transactionService.createTransaction(input));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionByTransactionId(@PathVariable("transactionId") UUID transactionId) {
        return ResponseEntity.ok(this.transactionService.getTransactionById(transactionId));
    }

    @GetMapping()
    public ResponseEntity<List<TransactionResponse>> getAllTransactionsByAccountNumber(
            @RequestParam(required = true) String accountNumber,
            @RequestParam(required = false) Status status
    ) {
        return ResponseEntity.ok(this.transactionService.getAllTransactionsByAccountNumber(accountNumber, status));
    }
}
