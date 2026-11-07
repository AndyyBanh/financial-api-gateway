package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import java.util.UUID;

// Called when the error handler gives up on a record: sends it to the DLT and reports it as FAILED.
@Slf4j
public class TransactionFailureRecoverer implements ConsumerRecordRecoverer {

    private final DeadLetterPublishingRecoverer deadLetterRecoverer;
    private final TransactionResultProducer transactionResultProducer;

    public TransactionFailureRecoverer(DeadLetterPublishingRecoverer deadLetterRecoverer, TransactionResultProducer transactionResultProducer) {
        this.deadLetterRecoverer = deadLetterRecoverer;
        this.transactionResultProducer = transactionResultProducer;
    }

    /**
     * Sends the failed record to the DLT, then reports the transaction as FAILED.
     * <p>
     * If the DLT send fails, this throws so the record is tried again instead of lost.
     *
     * @param record
     * @param exception
     */
    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        // Throws if the DLT publish fails, so the record is redelivered instead of being lost
        this.deadLetterRecoverer.accept(record, exception);

        // Use the record key (transactionId) since the value may not have been deserializable
        UUID transactionId;
        try {
            transactionId = UUID.fromString(String.valueOf(record.key()));
        } catch (IllegalArgumentException ex) {
            log.warn("Dead-lettered record has no valid transactionId key: {}", record.key());
            return;
        }

        try {
            this.transactionResultProducer.send(new TransactionResultEvent(transactionId, Status.FAILED));
        } catch (Exception ex) {
            log.error("Error sending FAILED result for dead-lettered transactionId={}", transactionId, ex);
        }
    }
}
