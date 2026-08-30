package com.tikitaka.space.dto.response;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.DayOfWeek;
import com.tikitaka.space.entity.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;

public record ScheduleResponse(

        @JsonProperty("day")
        @Schema(example = "MONDAY")
        DayOfWeek day,

        @JsonProperty("start_time")
        @Schema(type = "string", example = "09:00")
        LocalTime startTime,

        @JsonProperty("end_time")
        @Schema(type = "string", example = "10:30")
        LocalTime endTime
) {

    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getDay(),
                schedule.getStartTime(),
                schedule.getEndTime()
        );
    }
}