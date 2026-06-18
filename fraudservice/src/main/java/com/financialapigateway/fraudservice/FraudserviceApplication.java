package com.financialapigateway.fraudservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class FraudserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FraudserviceApplication.class, args);
	}

}
