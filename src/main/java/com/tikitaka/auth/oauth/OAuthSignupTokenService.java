package com.tikitaka.auth.oauth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.global.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class OAuthSignupTokenService {
    private static final String TOKEN_TYPE = "OAUTH_SIGNUP";
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final Duration ttl;
    private final SecretKey signingKey;

    public OAuthSignupTokenService(JwtProperties jwtProperties, Clock clock,
            @Value("${security.oauth-signup-token-ttl:10m}") Duration ttl) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.ttl = ttl;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    public String issue(OAuthProfile profile) {
        Instant now = clock.instant();
        var builder = Jwts.builder().issuer(jwtProperties.issuer()).subject(profile.providerUserId())
                .id(UUID.randomUUID().toString()).claim("token_type", TOKEN_TYPE)
                .claim("provider", profile.provider().name()).claim("name", profile.name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl)));
        if (profile.email() != null) builder.claim("email", profile.email());
        if (profile.profileUrl() != null) builder.claim("profile_url", profile.profileUrl());
        return builder.signWith(signingKey).compact();
    }

    public OAuthSignupClaims validate(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).requireIssuer(jwtProperties.issuer())
                .build().parseSignedClaims(token).getPayload();
        if (!TOKEN_TYPE.equals(claims.get("token_type", String.class))) {
            throw new IllegalArgumentException("Unexpected token type");
        }
        return new OAuthSignupClaims(AuthProvider.valueOf(claims.get("provider", String.class)),
                claims.getSubject(), claims.get("email", String.class), claims.get("name", String.class),
                claims.get("profile_url", String.class));
    }
}
