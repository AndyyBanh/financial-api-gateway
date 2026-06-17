package com.financialapigateway.fraudservice.kafka;

import com.financialapigateway.fraudservice.event.TransactionEvent;
import com.financialapigateway.fraudservice.service.FraudAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionListener {

    private final FraudAlertService alertService;

    @Autowired
    public TransactionListener(FraudAlertService  alertService) {
        this.alertService = alertService;
    }

    @KafkaListener(groupId = "fraud-group", topics = "${general.kafka-topic.transactions}")
    public void listen(TransactionEvent event) {
        this.alertService.processTransaction(event);
    }
}
