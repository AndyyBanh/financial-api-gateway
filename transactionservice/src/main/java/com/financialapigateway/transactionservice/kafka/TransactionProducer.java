package com.financialapigateway.transactionservice.kafka;

import com.financialapigateway.transactionservice.event.TransactionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TransactionProducer {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${general.kafka-topic.transactions}")
    private String topic;

    @Autowired
    public TransactionProducer(KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(TransactionEvent event) {
        this.kafkaTemplate.send(this.topic, event.getTransactionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        System.out.println("Error sending transaction: " + event.getTransactionId().toString());
                    } else {
                        System.out.println("Transaction sent successfully: " + event.getTransactionId().toString());
                    }
        });
    }

}
