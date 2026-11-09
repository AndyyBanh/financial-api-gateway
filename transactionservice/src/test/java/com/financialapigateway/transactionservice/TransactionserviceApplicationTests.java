package com.financialapigateway.transactionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

// "test" profile swaps the Docker Postgres for H2 and points Kafka at the embedded broker
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"transactions", "transaction-results"})
class TransactionserviceApplicationTests {

	@Test
	void contextLoads() {
	}

}
