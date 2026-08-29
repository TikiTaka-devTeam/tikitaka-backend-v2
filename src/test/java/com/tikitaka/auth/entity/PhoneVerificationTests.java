package com.tikitaka.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PhoneVerificationTests {

    @Test
    void tracksDeliveryVerificationAndConsumptionState() {
        Instant now = Instant.parse("2026-08-26T10:00:00Z");
        PhoneVerification verification = PhoneVerification.create(
                "01012345678",
                "127.0.0.1",
                "code-hash",
                now.plusSeconds(180),
                now.plusSeconds(60));

        verification.markSent(now);
        verification.recordFailedAttempt();
        verification.verify("token-hash", now.plusSeconds(10), now.plusSeconds(610));
        verification.consume(now.plusSeconds(20));

        assertThat(verification.getDeliveryStatus()).isEqualTo(PhoneVerificationDeliveryStatus.SENT);
        assertThat(verification.getAttemptCount()).isEqualTo(1);
        assertThat(verification.getVerificationTokenHash()).isEqualTo("token-hash");
        assertThat(verification.getConsumedAt()).isEqualTo(now.plusSeconds(20));
    }

    @Test
    void capsFailedAttemptsAtFive() {
        Instant now = Instant.parse("2026-08-26T10:00:00Z");
        PhoneVerification verification = PhoneVerification.create(
                "01012345678",
                "127.0.0.1",
                "code-hash",
                now.plusSeconds(180),
                now.plusSeconds(60));

        for (int attempt = 0; attempt < 6; attempt++) {
            verification.recordFailedAttempt();
        }

        assertThat(verification.getAttemptCount()).isEqualTo(5);
    }
}
