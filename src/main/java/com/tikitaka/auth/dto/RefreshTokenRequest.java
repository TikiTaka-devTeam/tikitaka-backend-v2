package com.tikitaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @Schema(description = "리프레시 토큰", example = "token")
        @NotBlank String refreshToken
) {
}
