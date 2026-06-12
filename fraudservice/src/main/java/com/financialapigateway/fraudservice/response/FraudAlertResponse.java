package com.financialapigateway.fraudservice.response;

import com.financialapigateway.fraudservice.enumeration.Reason;
import com.financialapigateway.fraudservice.enumeration.Severity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FraudAlertResponse {
    private UUID alertID;
    private UUID transactionID;
    private Reason reason;
    private Severity severity;
    private LocalDateTime createdAt;
}
