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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
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
        log.debug("Getting fraud alert alertId={}", alertId);
        FraudAlert alert = this.fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> {
                    log.warn("Fraud alert not found alertId={}", alertId);
                    return new FraudAlertNotFoundException("Fraud Alert not found");
                });
        return mapToResponse(alert);
    }


    public FraudAlertResponse getFraudAlertByTransactionId(UUID transactionId) {
        log.debug("Getting fraud alert transactionId={}", transactionId);
        FraudAlert alert = this.fraudAlertRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> {
                    log.warn("Fraud alert not found transactionId={}", transactionId);
                    return new FraudAlertNotFoundException("Fraud Alert not found");
                });
        return mapToResponse(alert);
    }

    /**
        Fraud detection logic

        Rules:
        1. Large Amounts (transactions over 10k get flagged)

        Need Feign/rest template add later
        2. High percentage of balance (if amount exceeds 80% of sender balance)
        3. Unusual freq (if the sender sent 10 or more transactions within 10 minutes)

     */
    public void processTransaction(TransactionEvent event) {
        log.debug("Checking transaction for fraud transactionId={}", event.getTransactionId());
        if (this.fraudAlertRepository.existsByTransactionId(event.getTransactionId())) {
            log.debug("Alert already exists, skipping transactionId={}", event.getTransactionId());
            return;
        }

        Reason reason = detectFraud(event);

        if (reason == null) {
            log.debug("No fraud detected transactionId={}", event.getTransactionId());
            return;
        }

        Severity severity = determineSeverity(reason);
        FraudAlert alert = mapToEntity(reason, severity, event);
        this.fraudAlertRepository.save(alert);
        log.info("Fraud alert created transactionId={} reason={} severity={}", event.getTransactionId(), reason, severity);
    }

    @Value("${fraud.large-amount-threshold}")
    private BigDecimal largeAmountThreshold;

    private static final BigDecimal HIGH_PERCENTAGE = new BigDecimal("0.80");

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

    private boolean isLargeAmount(BigDecimal amount) {
        return amount.compareTo(this.largeAmountThreshold) > 0;
    }


    private boolean isHighPercentage(TransactionEvent event) {
        AccountResponse sender = this.accountClient.getAccountByAccountNumber(event.getSenderId());

        if (sender.getBalance().signum() == 0) {
            return event.getAmount().signum() > 0;
        }
        return event.getAmount().compaclareTo(sender.getBalance().multiply(HIGH_PERCENTAGE)) >= 0;
    }

    /**
     * Checks whether the sender sent too many transactions in a short time.
     * <p>
     * The Transaction Service counts the sender's transactions from the last frequencyWindow
     * minutes and returns one number, not the full history. Received transactions don't count,
     * and the count includes this transaction. The window ends when this check runs, not when the
     * transaction was made, so if the consumer is behind the window can miss the transaction's time.
     *
     * @param event
     * @return true if the sender sent frequencyLimit or more transactions in the window
     */
    private boolean isUnusualFrequency(TransactionEvent event) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(this.frequencyWindow);
        long recentCount = this.transactionClient.countSentTransactionsSince(event.getSenderId(), since.toString());

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
