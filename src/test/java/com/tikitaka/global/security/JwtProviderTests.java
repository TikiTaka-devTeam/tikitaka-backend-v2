package com.tikitaka.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class JwtProviderTests {
    private static final Instant NOW = Instant.parse("2099-08-24T03:00:00Z");
    private static final String SECRET =
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=";

    @Test
    void issuesAndValidatesTypedTokenPair() {
        JwtProvider provider = provider(Duration.ofMinutes(15), Duration.ofDays(14));
        UUID userId = UUID.randomUUID();

        TokenPair pair = provider.issue(userId);

        assertThat(provider.validateAccessToken(pair.accessToken())).isEqualTo(userId);
        assertThat(provider.validateAccessTokenDetails(pair.accessToken()))
                .isEqualTo(new ValidatedAccessToken(
                        userId, NOW.plus(Duration.ofMinutes(15))));
        assertThat(provider.validateRefreshToken(pair.refreshToken())).isEqualTo(userId);
        assertThat(pair.accessTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(pair.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void rejectsTokenWhenTokenTypeDoesNotMatch() {
        JwtProvider provider = provider(Duration.ofMinutes(15), Duration.ofDays(14));
        TokenPair pair = provider.issue(UUID.randomUUID());

        assertThatThrownBy(() -> provider.validateAccessToken(pair.refreshToken()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider.validateRefreshToken(pair.accessToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        JwtProvider provider = provider(Duration.ofMinutes(15), Duration.ofDays(14));
        String token = provider.issue(UUID.randomUUID()).accessToken();
        String tamperedToken = "x" + token.substring(1);

        assertThatThrownBy(() -> provider.validateAccessToken(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    private JwtProvider provider(Duration accessTtl, Duration refreshTtl) {
        JwtProperties properties = new JwtProperties(
                "tikitaka",
                SECRET,
                accessTtl,
                refreshTtl,
                "test-pepper");
        return new JwtProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
