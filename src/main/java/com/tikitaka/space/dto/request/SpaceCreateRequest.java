package com.tikitaka.space.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SpaceCreateRequest(

        @JsonProperty("space_name")
        @NotBlank
        @Size(max = 255)
        @Schema(example = "운영체제")
        String spaceName,

        @JsonProperty("classroom")
        @Size(max = 100)
        @Schema(example = "SW101", nullable = true)
        String classroom,

        @JsonProperty("schedules")
        @NotEmpty
        List<@Valid ScheduleRequest> schedules
) {
}