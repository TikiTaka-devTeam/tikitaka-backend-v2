package com.tikitaka.space.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SpaceCreateRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "운영체제")
        String spaceName,

        @Size(max = 100)
        @Schema(example = "SW101", nullable = true)
        String classroom,

        @NotEmpty
        List<@Valid ScheduleRequest> schedules
) {
}
