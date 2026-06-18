package com.financialapigateway.transactionservice.feignclient;

import com.financialapigateway.transactionservice.response.AccountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "account-service", url = "${services.account-service.url}")
public interface AccountClient {

    @GetMapping("/api/v1/accounts")
    AccountResponse getAccountByAccountNumber(@RequestParam String accountNumber);
}
