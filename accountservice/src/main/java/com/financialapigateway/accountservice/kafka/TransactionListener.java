package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionEvent;
import com.financialapigateway.accountservice.event.TransactionResultEvent;
import com.financialapigateway.accountservice.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionListener {

    private final AccountService accountService;

    private final TransactionResultProducer transactionResultProducer;

    @Autowired
    public TransactionListener(AccountService accountService, TransactionResultProducer transactionResultProducer) {
        this.accountService = accountService;
        this.transactionResultProducer = transactionResultProducer;
    }

    @KafkaListener(groupId = "account-group", topics = "${general.kafka-topic.transactions}")
    public void listen(TransactionEvent event) {
        log.debug("Received transaction event transactionId={}", event.getTransactionId());
        // Send only after processTransaction has committed
        Status status = this.accountService.processTransaction(event);
        this.transactionResultProducer.send(new TransactionResultEvent(event.getTransactionId(), status));
    }
}
