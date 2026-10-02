package com.tikitaka.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OAuthRedirectUriTests {
    private static final String LOCAL = "http://localhost:5173/oauth/callback";
    private static final String PROD = "https://tikitaka-frontend-v2.tikitakadev2026.workers.dev/oauth/callback";
    private static final String DEFAULT = "https://backend.example/callback";

    @Test
    void bindsConfiguredCommaSeparatedAllowlistToProvider() {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                Map.of("oauth.google.client-id", "client", "oauth.google.client-secret", "secret",
                        "oauth.google.redirect-uri", DEFAULT,
                        "oauth.google.allowed-redirect-uris", LOCAL + "," + PROD));
        var config = new org.springframework.boot.context.properties.bind.Binder(source)
                .bind("oauth", OAuthProperties.class).get();
        assertThat(config.google().allowedRedirectUris()).containsExactly(LOCAL, PROD);
        assertThat(config.google().redirectUri()).isEqualTo(DEFAULT);
    }

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void exchangesCodeWithExactLocalProductionAndLegacyCallbacks(AuthProvider provider) {
        for (String requested : new String[]{LOCAL, PROD, null}) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            String expected = requested == null ? DEFAULT : requested;
            server.expect(requestTo(provider == AuthProvider.GOOGLE
                            ? "https://oauth2.googleapis.com/token" : "https://kauth.kakao.com/oauth/token"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("redirect_uri=" + URLEncoder.encode(expected, StandardCharsets.UTF_8))))
                    .andRespond(withSuccess("{\"access_token\":\"provider-token\"}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(provider == AuthProvider.GOOGLE
                            ? "https://openidconnect.googleapis.com/v1/userinfo" : "https://kapi.kakao.com/v2/user/me"))
                    .andExpect(header("Authorization", "Bearer provider-token"))
                    .andRespond(withSuccess(provider == AuthProvider.GOOGLE
                            ? "{\"sub\":\"123\",\"name\":\"Tester\"}"
                            : "{\"id\":123,\"kakao_account\":{\"profile\":{\"nickname\":\"Tester\"}}}", MediaType.APPLICATION_JSON));
            var config = new OAuthProperties.Provider("client", "secret", DEFAULT, List.of(LOCAL, PROD));
            var client = new RestOAuthProviderClient(builder, new OAuthProperties(config, config));
            var profile = requested == null ? client.fetchProfile(provider, "code")
                    : client.fetchProfile(provider, "code", requested);
            assertThat(profile.provider()).isEqualTo(provider);
            assertThat(profile.name()).isEqualTo("Tester");
            server.verify();
        }
    }

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void rejectsUnapprovedAndNearMatchUrisBeforeContactingProvider(AuthProvider provider) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var config = new OAuthProperties.Provider("client", "secret", DEFAULT, List.of(LOCAL, PROD));
        var client = new RestOAuthProviderClient(builder, new OAuthProperties(config, config));
        for (String uri : List.of("", LOCAL + "/", LOCAL + "?next=evil", PROD.replace("https:", "http:"),
                "https://evil.example/oauth/callback", " " + LOCAL)) {
            assertThatThrownBy(() -> client.fetchProfile(provider, "code", uri))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
        server.verify();
    }
}
