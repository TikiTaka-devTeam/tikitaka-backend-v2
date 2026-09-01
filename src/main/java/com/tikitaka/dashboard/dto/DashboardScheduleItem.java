package com.tikitaka.dashboard.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.DayOfWeek;

public record DashboardScheduleItem(
        DayOfWeek day,
        @JsonProperty("start_time") LocalTime startTime,
        @JsonProperty("end_time") LocalTime endTime,
        String classroom
) {
}
