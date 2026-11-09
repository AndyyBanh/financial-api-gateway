package com.financialapigateway.gatewayservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    // Captures the authentication the filter put in the security context for the next filter
    private final AtomicReference<Authentication> seenAuthentication = new AtomicReference<>();
    private final WebFilterChain chain = exchange -> ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .doOnNext(this.seenAuthentication::set)
            .then();

    @BeforeEach
    void setUp() {
        this.jwtService = new JwtService();
        // Not real key (same as JwtServiceTest)
        ReflectionTestUtils.setField(this.jwtService, "secretKey", "ZmluYW5jaWFsLWFwaS1nYXRld2F5LXNlY3JldC1rZXktZm9yLXRlc3Rpbmctb25seQ==");
        ReflectionTestUtils.setField(this.jwtService, "expiration", 3600000L);
        this.filter = new JwtAuthenticationFilter(this.jwtService);
    }

    private MockServerWebExchange exchangeWithToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transactions").header("Authorization", "Bearer " + token)
        );
    }

    private String tokenFor(String email, List<? extends GrantedAuthority> authorities) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        doReturn(authorities).when(auth).getAuthorities();
        return this.jwtService.generateToken(auth);
    }

    @Test
    void shouldAuthenticateFromRoleClaim() {
        String token = tokenFor("alice@test.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        StepVerifier.create(this.filter.filter(exchangeWithToken(token), this.chain)).verifyComplete();

        Authentication authentication = this.seenAuthentication.get();
        assertNotNull(authentication);
        assertEquals("alice@test.com", authentication.getName());
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void shouldRejectTokenWithoutRoleClaim() {
        String token = tokenFor("alice@test.com", List.of());
        MockServerWebExchange exchange = exchangeWithToken(token);

        StepVerifier.create(this.filter.filter(exchange, this.chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(this.seenAuthentication.get());
    }

    @Test
    void shouldRejectInvalidToken() {
        MockServerWebExchange exchange = exchangeWithToken("this.is.not.valid.jwt");

        StepVerifier.create(this.filter.filter(exchange, this.chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(this.seenAuthentication.get());
    }

    @Test
    void shouldRejectExpiredToken() {
        ReflectionTestUtils.setField(this.jwtService, "expiration", -1000L);
        String token = tokenFor("alice@test.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        MockServerWebExchange exchange = exchangeWithToken(token);

        StepVerifier.create(this.filter.filter(exchange, this.chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(this.seenAuthentication.get());
    }
}
