package com.financialapigateway.fraudservice.exceptions;

public class FraudAlertNotFoundException extends RuntimeException {
    public FraudAlertNotFoundException(String message) {
        super(message);
    }
}
