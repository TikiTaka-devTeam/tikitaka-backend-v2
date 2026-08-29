package com.tikitaka.space.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record SpaceUpdateRequest(
        @Size(min = 1, max = 255)
        @Schema(example = "운영체제 심화", nullable = true)
        String spaceName,

        @Size(max = 100)
        @Schema(example = "SW201", nullable = true)
        String classroom,

        List<@Valid ScheduleRequest> schedules
) {
}
