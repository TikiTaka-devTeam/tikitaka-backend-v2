package com.tikitaka.space.dto.response;

import java.util.List;
import java.util.UUID;

import com.tikitaka.space.entity.SpaceColorKey;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceCreateResponse(
        UUID spaceId,
        @Schema(example = "운영체제") String spaceName,
        @Schema(example = "2026") Integer year,
        @Schema(example = "2") String semester,
        @Schema(example = "SW101") String classroom,
        List<ScheduleResponse> schedules,
        @Schema(example = "COLOR_1") SpaceColorKey colorKey,
        @Schema(example = "A1B2C3D4") String spaceCode,
        @Schema(example = "ACTIVE") String status
) {
}
