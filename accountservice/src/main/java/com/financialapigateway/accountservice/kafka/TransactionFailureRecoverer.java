package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionResultEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import java.util.UUID;

// Runs after retries are exhausted: dead-letter the record, then tell transaction service it failed.
public class TransactionFailureRecoverer implements ConsumerRecordRecoverer {

    private final DeadLetterPublishingRecoverer deadLetterRecoverer;
    private final TransactionResultProducer transactionResultProducer;

    public TransactionFailureRecoverer(DeadLetterPublishingRecoverer deadLetterRecoverer, TransactionResultProducer transactionResultProducer) {
        this.deadLetterRecoverer = deadLetterRecoverer;
        this.transactionResultProducer = transactionResultProducer;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        // Throws if the DLT publish fails, so the record is redelivered instead of being lost
        this.deadLetterRecoverer.accept(record, exception);

        // Use the record key (transactionId) since the value may not have been deserializable
        UUID transactionId;
        try {
            transactionId = UUID.fromString(String.valueOf(record.key()));
        } catch (IllegalArgumentException ex) {
            System.out.println("Dead-lettered record has no valid transactionId key: " + record.key());
            return;
        }

        try {
            this.transactionResultProducer.send(new TransactionResultEvent(transactionId, Status.FAILED));
        } catch (Exception ex) {
            System.out.println("Error sending FAILED result for dead-lettered transaction " + transactionId + ": " + ex.getMessage());
        }
    }
}
