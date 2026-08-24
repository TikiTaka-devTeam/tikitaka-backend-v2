package com.tikitaka.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tikitaka.global.security.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class PhoneVerificationTokenService {
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TOKEN_TYPE = "PHONE_VERIFICATION";

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final Duration ttl;
    private final SecretKey signingKey;

    public PhoneVerificationTokenService(
            JwtProperties jwtProperties,
            Clock clock,
            @Value("${security.phone-verification-token-ttl:10m}") Duration ttl
    ) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.ttl = ttl;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    public String issue(String normalizedPhoneNumber) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(normalizedPhoneNumber)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public String validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Unexpected token type");
        }
        return claims.getSubject();
    }
}
