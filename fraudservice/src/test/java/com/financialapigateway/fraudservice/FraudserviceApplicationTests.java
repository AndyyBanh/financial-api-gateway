package com.financialapigateway.fraudservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

// "test" profile swaps the Docker Postgres for H2 and points Kafka at the embedded broker
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"transactions", "transactions-dlt"})
class FraudserviceApplicationTests {

	@Test
	void contextLoads() {
	}

}
