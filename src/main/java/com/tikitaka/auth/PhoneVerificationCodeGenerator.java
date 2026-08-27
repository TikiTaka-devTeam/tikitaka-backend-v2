package com.tikitaka.auth;

import java.security.SecureRandom;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class
PhoneVerificationCodeGenerator {
    private static final int CODE_BOUND = 1_000_000;
    private final SecureRandom secureRandom;

    public PhoneVerificationCodeGenerator() {
        this(new SecureRandom());
    }

    PhoneVerificationCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }
}
