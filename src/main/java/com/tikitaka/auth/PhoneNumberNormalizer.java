package com.tikitaka.auth;

import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {
    private static final String PHONE_NUMBER_PATTERN = "^01[016789][0-9]{7,8}$";

    public String normalize(String phoneNumber) {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phoneNumber must not be null");
        }
        String normalized = phoneNumber.replaceAll("[^0-9]", "");
        if (!normalized.matches(PHONE_NUMBER_PATTERN)) {
            throw new IllegalArgumentException("Invalid phone number format");
        }
        return normalized;
    }
}
