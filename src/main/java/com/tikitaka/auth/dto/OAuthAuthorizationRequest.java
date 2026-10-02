package com.tikitaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OAuthAuthorizationRequest(
        @Schema(description = "OAuth 공급자가 발급한 authorization code")
        @NotBlank String authorizationCode,
        @Schema(description = "인가 코드 요청에 사용한 콜백 URI. 허용 목록과 정확히 일치해야 하며, 생략 시 서버 기본 콜백 사용",
                example = "http://localhost:5173/oauth/callback")
        String redirectUri
) {
}
