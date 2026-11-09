package com.financialapigateway.accountservice.config;

import com.financialapigateway.accountservice.kafka.TransactionFailureRecoverer;
import com.financialapigateway.accountservice.kafka.TransactionResultProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    @Value("${general.kafka-topic.transactions-dlt}")
    private String dltTopic;

    @Bean
    public NewTopic transactionsDltTopic() {
        return TopicBuilder.name(dltTopic).partitions(1).replicas(1).build();
    }

    /**
     * Error handler for every Kafka listener in this service.
     * <p>
     * Retries a failed record with a growing wait. Errors that won't fix themselves are not retried.
     * When it gives up, {@link TransactionFailureRecoverer} sends the record to the DLT and reports
     * it as FAILED. Records in the DLT are not retried.
     *
     * @param kafkaProperties Spring Boot's Kafka settings from application.yaml
     * @param transactionResultProducer
     * @param maxRetries
     * @param initialInterval
     * @param multiplier
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaProperties kafkaProperties,
            TransactionResultProducer transactionResultProducer,
            @Value("${general.kafka.retry.max-retries}") int maxRetries,
            @Value("${general.kafka.retry.initial-interval}") long initialInterval,
            @Value("${general.kafka.retry.multiplier}") double multiplier) {

        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(
                dltTemplate(kafkaProperties),
                (record, ex) -> {
                    log.error("Sending key={} to {} after {}", record.key(), dltTopic, innerExceptionName(ex));
                    return new TopicPartition(dltTopic, -1);
                });
        TransactionFailureRecoverer recoverer = new TransactionFailureRecoverer(deadLetterRecoverer, transactionResultProducer);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, NullPointerException.class);
        // Log the record key (transactionId) and exception type only; values and messages can carry account data
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Delivery attempt {} failed for key={} ({})", deliveryAttempt, record.key(), innerExceptionName(ex)));
        return errorHandler;
    }

    /**
     * Returns the class name of the real error inside Spring's wrapper exception.
     * Only the name is logged, because error messages can contain account data.
     *
     * @param ex
     * @return the inner exception's class name, e.g. {@code ConnectException}
     */
    private static String innerExceptionName(Exception ex) {
        return NestedExceptionUtils.getMostSpecificCause(ex).getClass().getSimpleName();
    }

    /**
     * Builds the Kafka sender for the DLT. Unreadable records are sent as raw bytes, others as JSON.
     * <p>
     * Not a bean: a {@code KafkaTemplate} bean would replace the default one used by
     * {@link TransactionResultProducer}.
     *
     * @param kafkaProperties Spring Boot's Kafka settings from application.yaml
     */
    private KafkaTemplate<Object, Object> dltTemplate(KafkaProperties kafkaProperties) {
        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(Object.class, new JsonSerializer<>());

        DefaultKafkaProducerFactory<Object, Object> producerFactory = new DefaultKafkaProducerFactory<>(
                kafkaProperties.buildProducerProperties(),
                new DelegatingByTypeSerializer(Map.of(String.class, new StringSerializer(), byte[].class, new ByteArraySerializer())),
                new DelegatingByTypeSerializer(delegates, true));
        return new KafkaTemplate<>(producerFactory);
    }
}
