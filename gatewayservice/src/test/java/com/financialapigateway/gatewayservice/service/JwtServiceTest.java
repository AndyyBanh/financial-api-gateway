package com.financialapigateway.gatewayservice.service;

import com.financialapigateway.gatewayservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Not real key
        ReflectionTestUtils.setField(jwtService, "secretKey", "ZmluYW5jaWFsLWFwaS1nYXRld2F5LXNlY3JldC1rZXktZm9yLXRlc3Rpbmctb25seQ==");
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L);
    }

    @Test
    void shouldGenerateToken() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice@test.com");

        String token = this.jwtService.generateToken(auth);

        assertNotNull(token);
        assertTrue(this.jwtService.validateToken(token));
    }

    @Test
    void shouldExtractEmailFromToken() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice@test.com");

        String token = this.jwtService.generateToken(auth);

        assertEquals("alice@test.com", this.jwtService.getEmailFromJWT(token));
    }

    @Test
    void shouldRejectTamperedToken() {
        String tamperedToken = "this.is.not.valid.jwt";
        assertFalse(this.jwtService.validateToken(tamperedToken));
    }

    @Test
    void shouldRejectExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L);

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice@test.com");
        String token = this.jwtService.generateToken(auth);

        assertFalse(this.jwtService.validateToken(token));
    }
}
