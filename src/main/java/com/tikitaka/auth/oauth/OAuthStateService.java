package com.tikitaka.auth.oauth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.global.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class OAuthStateService {
    private static final String TOKEN_TYPE = "OAUTH_STATE";
    private static final Duration TTL = Duration.ofMinutes(5);
    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public OAuthStateService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String issue(AuthProvider provider) {
        Instant now = clock.instant();
        return Jwts.builder().issuer(properties.issuer()).subject(provider.name())
                .id(UUID.randomUUID().toString()).claim("token_type", TOKEN_TYPE)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(TTL)))
                .signWith(signingKey).compact();
    }

    public void validate(String state, AuthProvider provider) {
        Claims claims = Jwts.parser().verifyWith(signingKey).requireIssuer(properties.issuer())
                .build().parseSignedClaims(state).getPayload();
        if (!TOKEN_TYPE.equals(claims.get("token_type", String.class))
                || !provider.name().equals(claims.getSubject())) {
            throw new JwtException("Invalid OAuth state");
        }
    }
}
