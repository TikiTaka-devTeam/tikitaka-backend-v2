package com.tikitaka.space.dto.response;

import java.util.UUID;

import com.tikitaka.space.entity.SpaceColorKey;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceListResponse(
        UUID spaceId,
        @Schema(example = "운영체제") String spaceName,
        @Schema(example = "2026") Integer year,
        @Schema(example = "2") String semester,
        @Schema(example = "COLOR_1") SpaceColorKey colorKey,
        @Schema(example = "ACTIVE") String status
) {
}
