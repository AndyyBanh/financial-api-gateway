package com.financialapigateway.fraudservice.service;

import com.financialapigateway.fraudservice.entity.FraudAlert;
import com.financialapigateway.fraudservice.enumeration.Reason;
import com.financialapigateway.fraudservice.enumeration.Severity;
import com.financialapigateway.fraudservice.event.TransactionEvent;
import com.financialapigateway.fraudservice.exceptions.FraudAlertNotFoundException;
import com.financialapigateway.fraudservice.feignclient.AccountClient;
import com.financialapigateway.fraudservice.feignclient.TransactionClient;
import com.financialapigateway.fraudservice.repository.FraudAlertRepository;
import com.financialapigateway.fraudservice.response.AccountResponse;
import com.financialapigateway.fraudservice.response.FraudAlertResponse;
import com.financialapigateway.fraudservice.response.TransactionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FraudAlertService {

    private final FraudAlertRepository fraudAlertRepository;
    private final AccountClient accountClient;
    private final TransactionClient transactionClient;

    @Autowired
    public FraudAlertService(FraudAlertRepository fraudAlertRepository,  AccountClient accountClient, TransactionClient transactionClient) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.accountClient = accountClient;
        this.transactionClient = transactionClient;
    }

    public FraudAlertResponse getFraudAlertByAlertId(UUID alertId) {
        FraudAlert alert = this.fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new FraudAlertNotFoundException("Fraud Alert not found"));
        return mapToResponse(alert);
    }


    public FraudAlertResponse getFraudAlertByTransactionId(UUID transactionId) {
        FraudAlert alert = this.fraudAlertRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new FraudAlertNotFoundException("Fraud Alert not found"));
        return mapToResponse(alert);
    }

    /**
        Fraud detection logic

        Rules:
        1. Large Amounts (transactions over 10k get flagged)

        Need Feign/rest template add later
        2. High percentage of balance (if amount exceeds 80% of sender balance)
        3. Unusual freq (if more than 5 transactions within 10 minutes)

     */
    public void processTransaction(TransactionEvent event) {
        // Check if alert already exists
        if (this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())) {
            return;
        }

        Reason reason = detectFraud(event);

        if (reason != null) {
            Severity severity = determineSeverity(reason);
            FraudAlert alert = mapToEntity(reason, severity, event);
            this.fraudAlertRepository.save(alert);
            System.out.println("Fraud alert created for transaction " + event.getTransactionId());
        }
    }

    @Value("${fraud.large-amount-threshold}")
    private float largeAmountThreshold;

    @Value("${fraud.frequency-limit}")
    private float frequencyLimit;

    @Value("${fraud.frequency-window-limit}")
    private int frequencyWindow;

    private Reason detectFraud(TransactionEvent event) {
        if (isLargeAmount(event.getAmount())) {
            return Reason.LARGE_AMOUNT;
        }
        if (isHighPercentage(event)) {
            return Reason.SUSPICIOUS_PATTERN;
        }
        if (isUnusualFrequency(event)) {
            return Reason.UNUSUAL_FREQUENCY;
        }

        return null;
    }

    private boolean isLargeAmount(float amount) {
        return amount > this.largeAmountThreshold;
    }


    private boolean isHighPercentage(TransactionEvent event) {
        AccountResponse sender = this.accountClient.getAccountByAccountNumber(event.getSenderId());
        float percentage = event.getAmount() / sender.getBalance();
        return percentage >= 0.80f;
    }

    private boolean isUnusualFrequency(TransactionEvent event) {
        List<TransactionResponse> recent = this.transactionClient.getAllTransactionsByAccountNumber(event.getSenderId(), null);
        long recentCount = recent.stream()
                .filter(t -> t.getCreationDate()
                        .isAfter(LocalDateTime.now().minusMinutes(this.frequencyWindow)))
                .count();

        return recentCount >= frequencyLimit;
    }

    private Severity determineSeverity(Reason reason) {
        return switch (reason) {
            case LARGE_AMOUNT -> Severity.HIGH;
            case UNUSUAL_FREQUENCY -> Severity.LOW;
            case SUSPICIOUS_PATTERN -> Severity.MEDIUM;
        };
    }

    private FraudAlertResponse mapToResponse(FraudAlert fraudAlert) {
        FraudAlertResponse response = new FraudAlertResponse();
        response.setAlertID(fraudAlert.getAlertId());
        response.setTransactionID(fraudAlert.getTransactionId());
        response.setReason(fraudAlert.getReason());
        response.setSeverity(fraudAlert.getSeverity());
        response.setCreatedAt(fraudAlert.getCreatedAt());
        return response;
    }

    private FraudAlert mapToEntity(Reason reason, Severity severity, TransactionEvent event) {
        FraudAlert alert = new FraudAlert();
        alert.setTransactionId(event.getTransactionId());
        alert.setReason(reason);
        alert.setSeverity(severity);
        alert.setCreatedAt(LocalDateTime.now());
        return alert;
    }
}
