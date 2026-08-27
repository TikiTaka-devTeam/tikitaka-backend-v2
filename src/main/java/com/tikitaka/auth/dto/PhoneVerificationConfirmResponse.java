package com.tikitaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PhoneVerificationConfirmResponse(
        boolean verified,
        @JsonProperty("verification_token") String verificationToken,
        @JsonProperty("expires_in") long expiresIn
) {
}
