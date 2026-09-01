package com.tikitaka.dashboard.dto;

import java.time.LocalTime;
import java.util.UUID;

import com.tikitaka.space.entity.DayOfWeek;

public record DashboardTimetableRow(
        UUID spaceId,
        String spaceName,
        String classroom,
        DayOfWeek day,
        LocalTime startTime,
        LocalTime endTime
) {
}
