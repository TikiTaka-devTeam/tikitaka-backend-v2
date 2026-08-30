package com.tikitaka.space.dto.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.SpaceColorKey;
import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceCreateResponse(

        @JsonProperty("space_id")
        UUID spaceId,

        @JsonProperty("space_name")
        @Schema(example = "운영체제")
        String spaceName,

        @JsonProperty("year")
        @Schema(example = "2026")
        Integer year,

        @JsonProperty("semester")
        @Schema(example = "2")
        String semester,

        @JsonProperty("classroom")
        @Schema(example = "SW101")
        String classroom,

        @JsonProperty("schedules")
        List<ScheduleResponse> schedules,

        @JsonProperty("color_key")
        @Schema(example = "COLOR_1")
        SpaceColorKey colorKey,

        @JsonProperty("space_code")
        @Schema(example = "A1B2C3D4")
        String spaceCode,

        @JsonProperty("status")
        @Schema(example = "ACTIVE")
        String status
) {
}