package com.tikitaka.space.dto.request;

import java.time.LocalTime;

import com.tikitaka.space.entity.DayOfWeek;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ScheduleRequest(
        @NotNull
        @Schema(example = "MONDAY")
        DayOfWeek day,

        @NotNull
        @Schema(type = "string", example = "09:00")
        LocalTime startTime,

        @NotNull
        @Schema(type = "string", example = "10:30")
        LocalTime endTime
) {
}
