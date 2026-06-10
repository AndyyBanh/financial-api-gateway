package com.financialapigateway.gatewayservice.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserResponse {
    private UUID userId;
    private String email;
}
