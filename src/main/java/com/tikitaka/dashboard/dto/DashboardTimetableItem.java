package com.tikitaka.dashboard.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardTimetableItem(
        @JsonProperty("space_id") UUID spaceId,
        @JsonProperty("space_name") String spaceName,
        List<DashboardScheduleItem> schedules
) {
    public DashboardTimetableItem {
        schedules = List.copyOf(schedules);
    }
}
