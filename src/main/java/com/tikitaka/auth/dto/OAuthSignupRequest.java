package com.tikitaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.user.entity.AccountType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OAuthSignupRequest(
        @JsonProperty("signup_token") @NotBlank String signupToken,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 30) String name,
        @JsonProperty("phone_number")
        @NotBlank @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$") String phoneNumber,
        @JsonProperty("phone_verification_token") @NotBlank String phoneVerificationToken,
        @JsonProperty("account_type") @NotNull AccountType accountType,
        @NotBlank @Size(max = 100) String univ,
        @NotBlank @Size(max = 100) String major,
        @JsonProperty("member_id_number") @NotBlank @Size(max = 30) String memberIdNumber
) {
}
