package com.financialapigateway.transactionservice.controller;

import com.financialapigateway.transactionservice.dto.TransactionDto;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.response.TransactionResponse;
import com.financialapigateway.transactionservice.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
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
        log.debug("Received create transaction request");
        return ResponseEntity.ok(this.transactionService.createTransaction(input));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionByTransactionId(@PathVariable("transactionId") UUID transactionId) {
        log.debug("Received get transaction request transactionId={}", transactionId);
        return ResponseEntity.ok(this.transactionService.getTransactionById(transactionId));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countSentTransactionsSince(
            @RequestParam(required = true) String senderId,
            @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        log.debug("Received count sent transactions request since={}", since);
        return ResponseEntity.ok(this.transactionService.countSentTransactionsSince(senderId, since));
    }

    @GetMapping()
    public ResponseEntity<List<TransactionResponse>> getAllTransactionsByAccountNumber(
            @RequestParam(required = true) String accountNumber,
            @RequestParam(required = false) Status status
    ) {
        log.debug("Received get transactions by account number request status={}", status);
        return ResponseEntity.ok(this.transactionService.getAllTransactionsByAccountNumber(accountNumber, status));
    }
}
