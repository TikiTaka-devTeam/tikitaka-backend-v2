package com.tikitaka.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PhoneAvailabilityResponse(
        @Schema(description = "확인한 휴대폰 번호", example = "010-1234-5678") String phoneNumber,
        @Schema(description = "사용 가능 여부", example = "true") boolean available
) {
}
