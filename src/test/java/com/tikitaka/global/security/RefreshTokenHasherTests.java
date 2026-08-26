package com.tikitaka.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RefreshTokenHasherTests {
    @Test
    void hashesDeterministicallyWithoutStoringRawToken() {
        RefreshTokenHasher hasher = hasher("pepper-one");

        String hash = hasher.hash("refresh-token");

        assertThat(hash).hasSize(64).doesNotContain("refresh-token");
        assertThat(hasher.hash("refresh-token")).isEqualTo(hash);
        assertThat(hasher.matches("refresh-token", hash)).isTrue();
        assertThat(hasher.matches("different-token", hash)).isFalse();
    }

    @Test
    void differentPepperProducesDifferentHash() {
        assertThat(hasher("pepper-one").hash("refresh-token"))
                .isNotEqualTo(hasher("pepper-two").hash("refresh-token"));
    }

    @Test
    void rejectsBlankToken() {
        assertThatThrownBy(() -> hasher("pepper").hash(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RefreshTokenHasher hasher(String pepper) {
        return new RefreshTokenHasher(new JwtProperties(
                "tikitaka",
                "unused",
                Duration.ofMinutes(15),
                Duration.ofDays(14),
                pepper));
    }
}
