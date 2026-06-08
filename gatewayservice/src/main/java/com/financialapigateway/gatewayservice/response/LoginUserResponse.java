package com.financialapigateway.gatewayservice.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserResponse {
    private String token;
    private long expiration;
    private String email;
}
