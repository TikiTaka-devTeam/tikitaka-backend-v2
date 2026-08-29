package com.tikitaka.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.auth.oauth.OAuthProfile;
import com.tikitaka.auth.oauth.OAuthSignupClaims;
import com.tikitaka.auth.oauth.OAuthSignupTokenService;
import com.tikitaka.global.security.JwtProperties;

class OAuthSignupTokenServiceTests {
    private static final String SECRET =
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=";

    @Test
    void issuesAndValidatesTokenWithoutEmailOrProfileImage() {
        JwtProperties properties = new JwtProperties(
                "tikitaka", SECRET, Duration.ofMinutes(15), Duration.ofDays(14), "pepper");
        OAuthSignupTokenService service = new OAuthSignupTokenService(
                properties, Clock.fixed(Instant.parse("2099-08-29T04:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(10));
        OAuthProfile profile = new OAuthProfile(
                AuthProvider.KAKAO, "provider-user-id", null, "카카오 닉네임", null);

        String token = service.issue(profile);
        OAuthSignupClaims claims = service.validate(token);

        assertThat(claims.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(claims.providerUserId()).isEqualTo("provider-user-id");
        assertThat(claims.name()).isEqualTo("카카오 닉네임");
        assertThat(claims.email()).isNull();
        assertThat(claims.profileUrl()).isNull();
    }
}