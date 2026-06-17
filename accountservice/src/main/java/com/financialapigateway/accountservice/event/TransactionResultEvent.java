package com.financialapigateway.accountservice.event;

import com.financialapigateway.accountservice.enumeration.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResultEvent {
    private UUID transactionId;
    private Status status;
}
