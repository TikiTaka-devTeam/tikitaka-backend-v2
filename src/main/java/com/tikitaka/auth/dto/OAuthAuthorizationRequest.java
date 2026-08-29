package com.tikitaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OAuthAuthorizationRequest(
        @Schema(description = "OAuth 공급자가 발급한 authorization code")
        @NotBlank String authorizationCode
) {
}
