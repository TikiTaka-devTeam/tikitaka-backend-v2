package com.tikitaka.dashboard.dto;

import java.time.LocalTime;
import java.util.UUID;

import com.tikitaka.space.entity.DayOfWeek;
import com.tikitaka.space.entity.SpaceColorKey;

public record DashboardTimetableRow(
        UUID spaceId,
        String spaceName,
        SpaceColorKey colorKey,
        String classroom,
        DayOfWeek day,
        LocalTime startTime,
        LocalTime endTime
) {
}
