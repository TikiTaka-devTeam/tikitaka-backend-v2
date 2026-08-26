package com.tikitaka.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.tikitaka.global.security.JwtProperties;

import io.jsonwebtoken.JwtException;

class PhoneVerificationTokenServiceTests {
    private static final String SECRET =
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=";

    @Test
    void issuesTokenBoundToVerifiedPhoneNumber() {
        PhoneVerificationTokenService service = service("2099-08-24T03:00:00Z");

        String token = service.issue("01012345678");

        assertThat(service.validate(token)).isEqualTo("01012345678");
    }

    @Test
    void rejectsExpiredVerificationToken() {
        PhoneVerificationTokenService service = service("2020-01-01T00:00:00Z");

        String token = service.issue("01012345678");

        assertThatThrownBy(() -> service.validate(token)).isInstanceOf(JwtException.class);
    }

    private PhoneVerificationTokenService service(String instant) {
        JwtProperties properties = new JwtProperties(
                "tikitaka",
                SECRET,
                Duration.ofMinutes(15),
                Duration.ofDays(14),
                "pepper");
        return new PhoneVerificationTokenService(
                properties,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                Duration.ofMinutes(10));
    }
}
