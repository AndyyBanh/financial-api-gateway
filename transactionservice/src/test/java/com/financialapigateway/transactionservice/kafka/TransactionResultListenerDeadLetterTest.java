package com.financialapigateway.transactionservice.kafka;

import com.financialapigateway.transactionservice.config.KafkaErrorHandlingConfig;
import com.financialapigateway.transactionservice.event.TransactionResultEvent;
import com.financialapigateway.transactionservice.service.TransactionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = {KafkaAutoConfiguration.class, KafkaErrorHandlingConfig.class, TransactionResultListener.class},
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "general.kafka.retry.initial-interval=10",
                "general.kafka.retry.multiplier=1.0"
        })
@EmbeddedKafka(partitions = 1, topics = {"transaction-results", "transaction-results-dlt"})
public class TransactionResultListenerDeadLetterTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private TransactionService transactionService;

    private KafkaTemplate<String, String> producer;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        this.producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(embeddedKafka), new StringSerializer(), new StringSerializer()));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", embeddedKafka);
        this.consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        this.embeddedKafka.consumeFromEmbeddedTopics(this.consumer, "transaction-results-dlt");
    }

    @AfterEach
    void tearDown() {
        this.consumer.close();
    }

    @Test
    void shouldRetryThenDeadLetter() {
        UUID transactionId = UUID.randomUUID();
        doThrow(new RuntimeException("db down")).when(this.transactionService).updateTransactionStatus(any());

        this.producer.send("transaction-results", transactionId.toString(), resultJson(transactionId));

        verify(this.transactionService, timeout(10_000).times(4)).updateTransactionStatus(any(TransactionResultEvent.class));

        ConsumerRecord<String, String> dlt = awaitRecord(r -> transactionId.toString().equals(r.key()));
        assertEquals("transaction-group", headerValue(dlt, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP));
        assertTrue(dlt.value().contains(transactionId.toString()));
    }

    @Test
    void shouldNotRetryNonRetryableException() {
        UUID transactionId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("bad event")).when(this.transactionService).updateTransactionStatus(any());

        this.producer.send("transaction-results", transactionId.toString(), resultJson(transactionId));

        awaitRecord(r -> transactionId.toString().equals(r.key()));
        verify(this.transactionService, times(1)).updateTransactionStatus(any());
    }

    @Test
    void shouldDeadLetterMalformedMessageWithoutRetrying() {
        UUID transactionId = UUID.randomUUID();

        this.producer.send("transaction-results", transactionId.toString(), "not-json");

        ConsumerRecord<String, String> dlt = awaitRecord(r -> transactionId.toString().equals(r.key()));
        assertEquals("not-json", dlt.value());
        verify(this.transactionService, never()).updateTransactionStatus(any());
    }

    private String resultJson(UUID transactionId) {
        return "{\"transactionId\":\"" + transactionId + "\",\"status\":\"COMPLETE\"}";
    }

    private String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    // Records polled but not yet matched, so a single poll batch can satisfy several awaits
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    private ConsumerRecord<String, String> awaitRecord(Predicate<ConsumerRecord<String, String>> matcher) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (true) {
            for (ConsumerRecord<String, String> record : this.received) {
                if (matcher.test(record)) {
                    this.received.remove(record);
                    return record;
                }
            }
            if (System.currentTimeMillis() > deadline) {
                return fail("No matching record received; saw " + this.received.size() + " other records");
            }
            this.consumer.poll(Duration.ofMillis(200)).forEach(this.received::add);
        }
    }
}
