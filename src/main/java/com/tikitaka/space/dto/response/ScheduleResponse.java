package com.tikitaka.space.dto.response;

import java.time.LocalTime;

import com.tikitaka.space.entity.DayOfWeek;
import com.tikitaka.space.entity.Schedule;

import io.swagger.v3.oas.annotations.media.Schema;

public record ScheduleResponse(
        @Schema(example = "MONDAY")
        DayOfWeek day,
        @Schema(type = "string", example = "09:00")
        LocalTime startTime,
        @Schema(type = "string", example = "10:30")
        LocalTime endTime
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(schedule.getDay(), schedule.getStartTime(), schedule.getEndTime());
    }
}
