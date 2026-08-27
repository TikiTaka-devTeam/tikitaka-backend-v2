package com.tikitaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerificationConfirmRequest(
        @JsonProperty("phone_number")
        @Schema(description = "인증번호를 받은 휴대폰 번호", example = "010-1234-5678")
        @NotBlank
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$")
        String phoneNumber,

        @JsonProperty("verification_code")
        @Schema(description = "문자로 받은 6자리 인증번호", example = "123456")
        @NotBlank
        @Pattern(regexp = "^\\d{6}$")
        String verificationCode
) {
}