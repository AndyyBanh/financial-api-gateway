package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.entity.OutboxEvent;
import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionResultEvent;
import com.financialapigateway.accountservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

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
    private TransactionResultProducer transactionResultProducer;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        this.outboxRelay = new OutboxRelay(this.outboxEventRepository, this.transactionResultProducer, 500, 1000);
    }

    @Test
    void shouldSendAndDeletePendingEvents() {
        OutboxEvent first = outboxEvent(Status.COMPLETE);
        OutboxEvent second = outboxEvent(Status.FAILED);
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(first, second));
        when(this.transactionResultProducer.send(resultEventFor(first))).thenReturn(sent());
        when(this.transactionResultProducer.send(resultEventFor(second))).thenReturn(sent());

        this.outboxRelay.publishPendingEvents();

        verify(this.transactionResultProducer).send(resultEventFor(first));
        verify(this.transactionResultProducer).send(resultEventFor(second));
        verify(this.outboxEventRepository).deleteAllByIdInBatch(List.of(first.getId(), second.getId()));
    }

    @Test
    void shouldKeepEventWhenSendFails() {
        OutboxEvent event = outboxEvent(Status.COMPLETE);
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(event));
        when(this.transactionResultProducer.send(resultEventFor(event))).thenReturn(failed());

        this.outboxRelay.publishPendingEvents();

        verify(this.outboxEventRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void shouldStopBatchAfterFirstFailure() {
        OutboxEvent first = outboxEvent(Status.COMPLETE);
        OutboxEvent second = outboxEvent(Status.COMPLETE);
        OutboxEvent third = outboxEvent(Status.COMPLETE);
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(first, second, third));
        when(this.transactionResultProducer.send(resultEventFor(first))).thenReturn(sent());
        when(this.transactionResultProducer.send(resultEventFor(second))).thenReturn(failed());

        this.outboxRelay.publishPendingEvents();

        verify(this.transactionResultProducer, never()).send(resultEventFor(third));
        verify(this.outboxEventRepository).deleteAllByIdInBatch(List.of(first.getId()));
    }

    @Test
    void shouldDoNothingWhenOutboxIsEmpty() {
        when(this.outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of());

        this.outboxRelay.publishPendingEvents();

        verify(this.transactionResultProducer, never()).send(any());
        verify(this.outboxEventRepository, never()).deleteAllByIdInBatch(any());
    }

    private static OutboxEvent outboxEvent(Status status) {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), status, LocalDateTime.now());
    }

    private static TransactionResultEvent resultEventFor(OutboxEvent outboxEvent) {
        return new TransactionResultEvent(outboxEvent.getTransactionId(), outboxEvent.getStatus());
    }

    private static CompletableFuture<SendResult<String, TransactionResultEvent>> sent() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<String, TransactionResultEvent>> failed() {
        return CompletableFuture.failedFuture(new RuntimeException("kafka down"));
    }
}
