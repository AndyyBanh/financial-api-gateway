package com.financialapigateway.gatewayservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Service
public class JwtService {
    @Value("${JWT_SECRET_KEY}")
    private String secretKey;
    @Value("${JWT_EXPIRATION}")
    private Long expiration;

    public static final String ROLE_CLAIM = "role";
    private static final String ROLE_PREFIX = "ROLE_";

    // Decoded from secretKey on first use, then reused for every sign/verify
    private volatile SecretKey signingKey;

    /**
     * Signs a token for the logged-in user. The role (e.g. USER) goes in a claim so the gateway
     * can authorize later requests from the signed token alone, without a database lookup.
     */
    public String generateToken(Authentication authentication) {
        String email = authentication.getName();
        Date currentDate = new Date();
        Date expireDate = new Date(currentDate.getTime() + this.expiration);

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElse(null);

        return Jwts.builder()
                .subject(email)
                .claim(ROLE_CLAIM, role) // null leaves the claim out
                .issuedAt(currentDate)
                .expiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        SecretKey key = this.signingKey;
        if (key == null) {
            key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.secretKey));
            this.signingKey = key;
        }
        return key;
    }

    /**
     * Verifies the signature and expiry once and returns the claims.
     *
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmailFromJWT(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("JWT rejected: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public long getExpiration() {
        return this.expiration;
    }
}
