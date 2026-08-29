package com.tikitaka.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PhoneVerificationSecurityTests {

    @Test
    void generatesSixDigitCodesWithSecureRandom() {
        PhoneVerificationCodeGenerator generator = new PhoneVerificationCodeGenerator();
        Set<String> generatedCodes = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            String code = generator.generate();
            assertThat(code).matches("^[0-9]{6}$");
            generatedCodes.add(code);
        }

        assertThat(generatedCodes).hasSizeGreaterThan(1);
    }

    @Test
    void hashesCodeWithPhoneNumberAndPepper() {
        PhoneVerificationHasher hasher = new PhoneVerificationHasher("test-pepper");
        String hash = hasher.hashCode("01012345678", "123456");

        assertThat(hash).hasSize(64).doesNotContain("123456");
        assertThat(hasher.matchesCode("01012345678", "123456", hash)).isTrue();
        assertThat(hasher.matchesCode("01099999999", "123456", hash)).isFalse();
        assertThat(hasher.matchesCode("01012345678", "654321", hash)).isFalse();
    }

    @Test
    void generatesAndHashesOpaque256BitTokens() {
        PhoneVerificationTokenGenerator generator = new PhoneVerificationTokenGenerator();
        PhoneVerificationHasher hasher = new PhoneVerificationHasher("test-pepper");

        String token = generator.generate();
        String anotherToken = generator.generate();
        String hash = hasher.hashToken(token);

        assertThat(token).hasSize(43).isNotEqualTo(anotherToken);
        assertThat(hash).hasSize(64).doesNotContain(token);
        assertThat(hasher.matchesToken(token, hash)).isTrue();
        assertThat(hasher.matchesToken(anotherToken, hash)).isFalse();
    }
}
