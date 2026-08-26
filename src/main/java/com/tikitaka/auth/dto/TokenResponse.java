package com.tikitaka.auth.dto;

import com.tikitaka.global.security.TokenPair;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "새 액세스 토큰", example = "new-access-token") String accessToken,
        @Schema(description = "새 리프레시 토큰", example = "new-refresh-token") String refreshToken
) {
    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken());
    }
}
