package com.tikitaka.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileImageResponse(
        @Schema(description = "프로필 이미지 URL", nullable = true) String profileUrl
) {
}