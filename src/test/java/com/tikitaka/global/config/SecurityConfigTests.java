package com.tikitaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigTests {
    @Test
    void passwordEncoderUsesOneWayBcryptHash() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        String encoded = encoder.encode("Test1234!");

        assertThat(encoded).startsWith("$2");
        assertThat(encoded).isNotEqualTo("Test1234!");
        assertThat(encoder.matches("Test1234!", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void allowsConfiguredWorkerOriginPreflightWithJwtHeaders() throws IOException {
        String frontendOrigin = "https://tikitaka-frontend-v2.tikitakadev2026.workers.dev";
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(
                new CorsProperties(List.of("http://localhost:5173", frontendOrigin)));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/actuator/health");
        request.addHeader(HttpHeaders.ORIGIN, frontendOrigin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean accepted = new DefaultCorsProcessor().processRequest(
                source.getCorsConfiguration(request), request, response);

        assertThat(accepted).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(frontendOrigin);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .contains("Authorization", "Content-Type");
    }

    @Test
    void rejectsUnconfiguredOriginWithoutUsingWildcard() throws IOException {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(
                new CorsProperties(List.of("http://localhost:5173")));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/spaces");
        request.addHeader(HttpHeaders.ORIGIN, "https://untrusted.example.com");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean accepted = new DefaultCorsProcessor().processRequest(
                source.getCorsConfiguration(request), request, response);

        assertThat(accepted).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
