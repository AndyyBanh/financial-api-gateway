package com.financialapigateway.transactionservice.kafka;

import com.financialapigateway.transactionservice.event.TransactionResultEvent;
import com.financialapigateway.transactionservice.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionResultListener {

    private final TransactionService transactionService;

    @Autowired
    public TransactionResultListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @KafkaListener(groupId = "transaction-group", topics = "${general.kafka-topic.transaction-results}")
    public void listen(TransactionResultEvent transactionResultEvent) {
        this.transactionService.updateTransactionStatus(transactionResultEvent);
    }

}
