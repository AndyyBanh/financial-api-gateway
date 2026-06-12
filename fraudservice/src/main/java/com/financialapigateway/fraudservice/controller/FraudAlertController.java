package com.financialapigateway.fraudservice.controller;

import com.financialapigateway.fraudservice.response.FraudAlertResponse;
import com.financialapigateway.fraudservice.service.FraudAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudAlertController {

    private final FraudAlertService fraudAlertService;

    @Autowired
    public FraudAlertController(FraudAlertService fraudAlertService) {
        this.fraudAlertService = fraudAlertService;
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<FraudAlertResponse> getFraudAlertByAlertId(@PathVariable UUID alertId) {
        FraudAlertResponse response = this.fraudAlertService.getFraudAlertByAlertId(alertId);
        return ResponseEntity.ok(response);
    }

    @GetMapping()
    public ResponseEntity<FraudAlertResponse> getFraudAlertByTransactionId(@RequestParam UUID transactionId) {
        FraudAlertResponse response = this.fraudAlertService.getFraudAlertByTransactionId(transactionId);
        return ResponseEntity.ok(response);
    }
}
