package com.financialapigateway.transactionservice.kafka;

import com.financialapigateway.transactionservice.entity.OutboxEvent;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.event.TransactionEvent;
import com.financialapigateway.transactionservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TransactionProducer transactionProducer;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        this.outboxRelay = new OutboxRelay(this.outboxEventRepository, this.transactionProducer, 500, 1000);
    }

    @Test
    void shouldSendAndDeletePendingEvents() {
        OutboxEvent first = outboxEvent();
        OutboxEvent second = outboxEvent();
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(first, second));
        when(this.transactionProducer.send(eventFor(first))).thenReturn(sent());
        when(this.transactionProducer.send(eventFor(second))).thenReturn(sent());

        this.outboxRelay.publishPendingEvents();

        verify(this.transactionProducer).send(eventFor(first));
        verify(this.transactionProducer).send(eventFor(second));
        verify(this.outboxEventRepository).deleteAllByIdInBatch(List.of(first.getId(), second.getId()));
    }

    @Test
    void shouldKeepEventWhenSendFails() {
        OutboxEvent event = outboxEvent();
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(event));
        when(this.transactionProducer.send(eventFor(event))).thenReturn(failed());

        this.outboxRelay.publishPendingEvents();

        verify(this.outboxEventRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void shouldStopBatchAfterFirstFailure() {
        OutboxEvent first = outboxEvent();
        OutboxEvent second = outboxEvent();
        OutboxEvent third = outboxEvent();
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(first, second, third));
        when(this.transactionProducer.send(eventFor(first))).thenReturn(sent());
        when(this.transactionProducer.send(eventFor(second))).thenReturn(failed());

        this.outboxRelay.publishPendingEvents();

        verify(this.transactionProducer, never()).send(eventFor(third));
        verify(this.outboxEventRepository).deleteAllByIdInBatch(List.of(first.getId()));
    }

    @Test
    void shouldDoNothingWhenOutboxIsEmpty() {
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of());

        this.outboxRelay.publishPendingEvents();

        verify(this.transactionProducer, never()).send(any());
        verify(this.outboxEventRepository, never()).deleteAllByIdInBatch(any());
    }

    private static OutboxEvent outboxEvent() {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "123", "321", new BigDecimal("50.00"), Status.PENDING, LocalDateTime.now());
    }

    private static TransactionEvent eventFor(OutboxEvent outboxEvent) {
        return new TransactionEvent(outboxEvent.getTransactionId(), outboxEvent.getSenderId(), outboxEvent.getRecipientId(),
                outboxEvent.getAmount(), outboxEvent.getStatus(), outboxEvent.getCreatedAt());
    }

    private static CompletableFuture<SendResult<String, TransactionEvent>> sent() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<String, TransactionEvent>> failed() {
        return CompletableFuture.failedFuture(new RuntimeException("kafka down"));
    }
}
