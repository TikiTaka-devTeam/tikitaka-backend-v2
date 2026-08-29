package com.tikitaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthProfileResponse(
        @Schema(example = "student@example.com") String email,
        @Schema(example = "김선민") String name,
        @Schema(example = "https://example.com/profile.jpg") String profileUrl
) {
}
