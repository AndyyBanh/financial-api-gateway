package com.financialapigateway.accountservice.kafka;

import com.financialapigateway.accountservice.config.KafkaErrorHandlingConfig;
import com.financialapigateway.accountservice.event.TransactionEvent;
import com.financialapigateway.accountservice.service.AccountService;
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
        classes = {KafkaAutoConfiguration.class, KafkaErrorHandlingConfig.class, TransactionListener.class, TransactionResultProducer.class},
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "general.kafka.retry.initial-interval=10",
                "general.kafka.retry.multiplier=1.0"
        })
@EmbeddedKafka(partitions = 1, topics = {"transactions", "transaction-results", "transactions-dlt"})
public class TransactionListenerDeadLetterTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private AccountService accountService;

    private KafkaTemplate<String, String> producer;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        this.producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(embeddedKafka), new StringSerializer(), new StringSerializer()));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", embeddedKafka);
        this.consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        this.embeddedKafka.consumeFromEmbeddedTopics(this.consumer, "transactions-dlt", "transaction-results");
    }

    @AfterEach
    void tearDown() {
        this.consumer.close();
    }

    @Test
    void shouldRetryThenDeadLetterAndSendFailed() {
        UUID transactionId = UUID.randomUUID();
        when(this.accountService.processTransaction(any())).thenThrow(new RuntimeException("db down"));

        this.producer.send("transactions", transactionId.toString(), eventJson(transactionId));

        verify(this.accountService, timeout(10_000).times(4)).processTransaction(any(TransactionEvent.class));

        ConsumerRecord<String, String> dlt = awaitRecord(r -> r.topic().equals("transactions-dlt") && transactionId.toString().equals(r.key()));
        assertEquals("account-group", headerValue(dlt, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP));
        assertTrue(dlt.value().contains(transactionId.toString()));

        ConsumerRecord<String, String> result = awaitRecord(r -> r.topic().equals("transaction-results") && transactionId.toString().equals(r.key()));
        assertTrue(result.value().contains("FAILED"));
    }

    @Test
    void shouldNotRetryNonRetryableException() {
        UUID transactionId = UUID.randomUUID();
        when(this.accountService.processTransaction(any())).thenThrow(new IllegalArgumentException("bad event"));

        this.producer.send("transactions", transactionId.toString(), eventJson(transactionId));

        awaitRecord(r -> r.topic().equals("transactions-dlt") && transactionId.toString().equals(r.key()));
        verify(this.accountService, times(1)).processTransaction(any());
    }

    @Test
    void shouldDeadLetterMalformedMessageWithoutRetrying() {
        UUID transactionId = UUID.randomUUID();

        this.producer.send("transactions", transactionId.toString(), "not-json");

        ConsumerRecord<String, String> dlt = awaitRecord(r -> r.topic().equals("transactions-dlt") && transactionId.toString().equals(r.key()));
        assertEquals("not-json", dlt.value());

        ConsumerRecord<String, String> result = awaitRecord(r -> r.topic().equals("transaction-results") && transactionId.toString().equals(r.key()));
        assertTrue(result.value().contains("FAILED"));
        verify(this.accountService, never()).processTransaction(any());
    }

    @Test
    void shouldDeadLetterMalformedMessageWithoutKey() {
        String marker = "not-json-" + UUID.randomUUID();

        this.producer.send("transactions", null, marker);

        ConsumerRecord<String, String> dlt = awaitRecord(r -> r.topic().equals("transactions-dlt") && marker.equals(r.value()));
        assertNull(dlt.key());
    }

    private String eventJson(UUID transactionId) {
        return "{\"transactionId\":\"" + transactionId + "\",\"senderId\":\"123\",\"recipientId\":\"321\",\"amount\":50.0,\"status\":\"PENDING\"}";
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
