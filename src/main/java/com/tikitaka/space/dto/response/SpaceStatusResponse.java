package com.tikitaka.space.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceStatusResponse(
        UUID spaceId,
        @Schema(example = "ARCHIVED") String status
) {
}
