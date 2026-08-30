package com.tikitaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerificationSendRequest(
        @JsonProperty("phone_number")
        @Schema(description = "인증번호를 받을 휴대폰 번호", example = "010-1234-5678")
        @NotBlank
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$")
        String phoneNumber
) {
}