package com.financialapigateway.fraudservice.service;

import com.financialapigateway.fraudservice.entity.FraudAlert;
import com.financialapigateway.fraudservice.exceptions.FraudAlertNotFoundException;
import com.financialapigateway.fraudservice.repository.FraudAlertRepository;
import com.financialapigateway.fraudservice.response.FraudAlertResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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


    private FraudAlertResponse mapToResponse(FraudAlert fraudAlert) {
        FraudAlertResponse response = new FraudAlertResponse();
        response.setAlertID(fraudAlert.getAlertId());
        response.setTransactionID(fraudAlert.getTransactionId());
        response.setReason(fraudAlert.getReason());
        response.setSeverity(fraudAlert.getSeverity());
        response.setCreatedAt(fraudAlert.getCreatedAt());
        return response;
    }
}
