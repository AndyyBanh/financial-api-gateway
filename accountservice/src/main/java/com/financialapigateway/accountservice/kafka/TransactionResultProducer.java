package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.event.TransactionResultEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TransactionResultProducer {

    private final KafkaTemplate<String, TransactionResultEvent> kafkaTemplate;

    @Value("${general.kafka-topic.transaction-results}")
    private String topic;

    @Autowired
    public TransactionResultProducer(KafkaTemplate<String, TransactionResultEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    public void send(TransactionResultEvent transactionResultEvent) {
        this.kafkaTemplate.send(topic, transactionResultEvent.getTransactionId().toString(), transactionResultEvent)
                .whenComplete((result, ex) ->  {
                    if (ex != null) {
                        System.out.println("Error sending transaction result" + transactionResultEvent.getTransactionId().toString());
                    } else {
                        System.out.println("Transaction sent successfully" + transactionResultEvent.getTransactionId().toString());
                    }
                });
    }
}
