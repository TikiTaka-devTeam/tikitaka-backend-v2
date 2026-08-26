package com.tikitaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmailAvailabilityResponse(
        @Schema(description = "확인한 이메일", example = "user@example.com") String email,
        @Schema(description = "사용 가능 여부", example = "true") boolean available
) {
}
