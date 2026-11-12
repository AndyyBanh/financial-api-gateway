package com.financialapigateway.transactionservice.kafka;

import com.financialapigateway.transactionservice.event.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TransactionProducer {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${general.kafka-topic.transactions}")
    private String topic;

    @Autowired
    public TransactionProducer(KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a transaction event to Kafka.
     *
     * @param event
     * @return completes when Kafka confirms the send, or fails if it doesn't
     */
    public CompletableFuture<SendResult<String, TransactionEvent>> send(TransactionEvent event) {
        log.debug("Producing transaction event transactionId={} topic={}", event.getTransactionId(), this.topic);
        return this.kafkaTemplate.send(this.topic, event.getTransactionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error sending transaction event transactionId={}", event.getTransactionId(), ex);
                    }
        });
    }

}
