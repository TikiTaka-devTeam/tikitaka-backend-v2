package com.tikitaka.auth.dto;

import java.util.UUID;

import com.tikitaka.global.security.TokenPair;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "액세스 토큰", example = "token")
        String accessToken,
        @Schema(description = "리프레시 토큰", example = "token")
        String refreshToken,
        @Schema(description = "로그인 사용자 요약")
        UserSummary user
) {
    public static LoginResponse of(TokenPair tokens, User user) {
        return new LoginResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                new UserSummary(user.getId(), user.getName(), user.getAccountType()));
    }

    public record UserSummary(
            @Schema(description = "사용자 ID", example = "123e4567-e89b-12d3-a456-426614174000") UUID userId,
            @Schema(description = "이름", example = "김선민") String name,
            @Schema(description = "계정 유형", example = "STUDENT") AccountType accountType
    ) {
    }
}
