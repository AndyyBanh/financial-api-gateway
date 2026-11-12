package com.financialapigateway.transactionservice.kafka;

import com.financialapigateway.transactionservice.entity.OutboxEvent;
import com.financialapigateway.transactionservice.event.TransactionEvent;
import com.financialapigateway.transactionservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionProducer transactionProducer;
    private final int batchSize;
    private final long sendTimeoutMs;

    @Autowired
    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       TransactionProducer transactionProducer,
                       @Value("${general.outbox.batch-size}") int batchSize,
                       @Value("${general.outbox.send-timeout-ms}") long sendTimeoutMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.transactionProducer = transactionProducer;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    /**
     * Sends waiting outbox rows to Kafka, oldest first, and deletes each one Kafka confirms.
     * <p>
     * A row only exists until its send is confirmed, so every row in the table still needs sending.
     * Stops at the first failed send (Kafka is likely down); the remaining rows are retried next run.
     * Runs again poll-interval-ms after the previous run finishes, so runs never overlap.
     */
    @Scheduled(fixedDelayString = "${general.outbox.poll-interval-ms}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = this.outboxEventRepository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, this.batchSize));
        if (pending.isEmpty()) {
            return;
        }
        log.debug("Publishing outbox events count={}", pending.size());

        List<UUID> sentIds = new ArrayList<>();
        for (OutboxEvent row : pending) {
            try {
                this.transactionProducer.send(mapToEvent(row)).get(this.sendTimeoutMs, TimeUnit.MILLISECONDS);
                sentIds.add(row.getId());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.warn("Outbox send failed transactionId={} ({}), will retry next run",
                        row.getTransactionId(), NestedExceptionUtils.getMostSpecificCause(ex).getClass().getSimpleName());
                break;
            }
        }

        if (!sentIds.isEmpty()) {
            this.outboxEventRepository.deleteAllByIdInBatch(sentIds);
        }
    }

    private TransactionEvent mapToEvent(OutboxEvent row) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(row.getTransactionId());
        event.setSenderId(row.getSenderId());
        event.setRecipientId(row.getRecipientId());
        event.setAmount(row.getAmount());
        event.setStatus(row.getStatus());
        event.setCreatedAt(row.getCreatedAt());
        return event;
    }
}
