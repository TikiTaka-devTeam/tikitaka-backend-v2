package com.tikitaka.space.dto.request;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.DayOfWeek;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ScheduleRequest(

        @JsonProperty("day")
        @NotNull
        @Schema(example = "MONDAY")
        DayOfWeek day,

        @JsonProperty("start_time")
        @NotNull
        @Schema(type = "string", example = "09:00")
        LocalTime startTime,

        @JsonProperty("end_time")
        @NotNull
        @Schema(type = "string", example = "10:30")
        LocalTime endTime
) {
}