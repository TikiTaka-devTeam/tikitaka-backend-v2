package com.tikitaka.auth.dto;

import com.tikitaka.user.entity.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OAuthSignupRequest(
        @NotBlank String signupToken,
        @NotBlank @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$") String phoneNumber,
        @NotBlank String phoneVerificationToken,
        @NotNull AccountType accountType,
        @NotBlank @Size(max = 100) String univ,
        @NotBlank @Size(max = 100) String major,
        @NotBlank @Size(max = 30) String memberIdNumber
) {
}
