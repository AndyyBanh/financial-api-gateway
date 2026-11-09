package com.financialapigateway.transactionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Value("${general.account-lookup.threads}")
    private int accountLookupThreads;

    /**
     * Threads for the recipient account lookup, so it runs at the same time as the sender lookup
     * when a transaction is created. Spring shuts the pool down with the application.
     */
    @Bean
    public Executor accountLookupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(this.accountLookupThreads);
        executor.setMaxPoolSize(this.accountLookupThreads);
        executor.setThreadNamePrefix("account-lookup-");
        executor.initialize();
        return executor;
    }
}
