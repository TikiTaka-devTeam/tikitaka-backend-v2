package com.tikitaka.global.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {
    private static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = createSigningKey(properties.secret());
    }

    public TokenPair issue(UUID userId) {
        Instant issuedAt = clock.instant();
        Instant accessExpiresAt = issuedAt.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(properties.refreshTokenTtl());

        return new TokenPair(
                createToken(userId, TokenType.ACCESS, issuedAt, accessExpiresAt),
                accessExpiresAt,
                createToken(userId, TokenType.REFRESH, issuedAt, refreshExpiresAt),
                refreshExpiresAt);
    }

    public UUID validateAccessToken(String token) {
        return validate(token, TokenType.ACCESS);
    }

    public UUID validateRefreshToken(String token) {
        return validate(token, TokenType.REFRESH);
    }

    private String createToken(UUID userId, TokenType type, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, type.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private UUID validate(String token, TokenType expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!expectedType.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Unexpected token type");
        }
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new JwtException("Invalid token subject", exception);
        }
    }

    private SecretKey createSigningKey(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret must be configured");
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodedSecret));
    }
}
