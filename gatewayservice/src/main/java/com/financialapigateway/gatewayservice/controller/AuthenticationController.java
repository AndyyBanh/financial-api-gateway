package com.financialapigateway.gatewayservice.controller;

import com.financialapigateway.gatewayservice.dto.LoginUserDto;
import com.financialapigateway.gatewayservice.dto.RegisterUserDto;
import com.financialapigateway.gatewayservice.response.LoginUserResponse;
import com.financialapigateway.gatewayservice.response.RegisterUserResponse;
import com.financialapigateway.gatewayservice.service.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @Autowired
    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginUserResponse>> login(@RequestBody LoginUserDto loginUserDto) {
        log.debug("Received login request");
        return this.authenticationService.login(loginUserDto).map(ResponseEntity::ok);
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterUserResponse>> register(@RequestBody RegisterUserDto registerUserDto) {
        log.debug("Received register request");
        return this.authenticationService.register(registerUserDto).map(ResponseEntity::ok);
    }

}
