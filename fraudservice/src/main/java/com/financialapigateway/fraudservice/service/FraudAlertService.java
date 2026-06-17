package com.financialapigateway.fraudservice.service;

import com.financialapigateway.fraudservice.entity.FraudAlert;
import com.financialapigateway.fraudservice.enumeration.Reason;
import com.financialapigateway.fraudservice.enumeration.Severity;
import com.financialapigateway.fraudservice.event.TransactionEvent;
import com.financialapigateway.fraudservice.exceptions.FraudAlertNotFoundException;
import com.financialapigateway.fraudservice.repository.FraudAlertRepository;
import com.financialapigateway.fraudservice.response.FraudAlertResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FraudAlertService {

    private final FraudAlertRepository fraudAlertRepository;

    @Autowired
    public FraudAlertService(FraudAlertRepository fraudAlertRepository) {
        this.fraudAlertRepository = fraudAlertRepository;
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

    private Reason detectFraud(TransactionEvent event) {
        if (isLargeAmount(event.getAmount())) {
            return Reason.LARGE_AMOUNT;
        }
        // Add high percentage and unusual freq implementation later since we will need feign calls
        return null;
    }

    private boolean isLargeAmount(float amount) {
        return amount > this.largeAmountThreshold;
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
