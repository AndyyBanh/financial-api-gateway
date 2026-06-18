package com.financialapigateway.fraudservice.feignclient;

import com.financialapigateway.fraudservice.enumeration.Status;
import com.financialapigateway.fraudservice.response.TransactionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "transaction-client", url = "${services.transaction-service.url}")
public interface TransactionClient {

    @GetMapping("/api/v1/transactions")
    List<TransactionResponse> getAllTransactionsByAccountNumber(@RequestParam(required = true) String accountNumber,
                                                                @RequestParam(required = false) Status status);
}
