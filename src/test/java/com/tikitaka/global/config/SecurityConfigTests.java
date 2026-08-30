package com.tikitaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
