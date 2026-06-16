package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.event.TransactionEvent;
import com.financialapigateway.accountservice.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionListener {

    private final AccountService accountService;

    @Autowired
    public TransactionListener(AccountService accountService) {
        this.accountService = accountService;
    }

    @KafkaListener(groupId = "account-group", topics = "${general.kafka-topic.transactions}")
    public void listen(TransactionEvent event) {
        this.accountService.processTransaction(event);
    }
}
