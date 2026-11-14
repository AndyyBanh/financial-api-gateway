package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.event.TransactionResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TransactionResultProducer {

    private final KafkaTemplate<String, TransactionResultEvent> kafkaTemplate;

    @Value("${general.kafka-topic.transaction-results}")
    private String topic;

    @Autowired
    public TransactionResultProducer(KafkaTemplate<String, TransactionResultEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    /**
     * Sends a transaction result to Kafka.
     *
     * @param transactionResultEvent
     * @return completes when Kafka confirms the send, or fails if it doesn't
     */
    public CompletableFuture<SendResult<String, TransactionResultEvent>> send(TransactionResultEvent transactionResultEvent) {
        log.debug("Producing transaction result transactionId={} status={} topic={}", transactionResultEvent.getTransactionId(), transactionResultEvent.getStatus(), topic);
        return this.kafkaTemplate.send(topic, transactionResultEvent.getTransactionId().toString(), transactionResultEvent)
                .whenComplete((result, ex) ->  {
                    if (ex != null) {
                        log.error("Error sending transaction result transactionId={}", transactionResultEvent.getTransactionId(), ex);
                    }
                });
    }
}
