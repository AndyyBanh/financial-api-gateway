package com.financialapigateway.gatewayservice;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

// "test" profile swaps the Docker Postgres for H2 and replaces the JWT secret with a dummy key
@SpringBootTest
@ActiveProfiles("test")
class GatewayserviceApplicationTests {

	// Replaces RedisConfig's bean, which opens a Redis connection on startup
	@MockitoBean
	private ProxyManager<String> proxyManager;

	@Value("${JWT_SECRET_KEY}")
	private String jwtSecretKey;

	@Test
	void contextLoads() {
	}

	@Test
	void usesDummyJwtSecretInsteadOfEnvFile() {
		assertEquals("ZmluYW5jaWFsLWFwaS1nYXRld2F5LXNlY3JldC1rZXktZm9yLXRlc3Rpbmctb25seQ==", this.jwtSecretKey);
	}

}
