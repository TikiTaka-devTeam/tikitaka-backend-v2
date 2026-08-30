package com.tikitaka.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardAssignmentItem(
        @JsonProperty("assignment_id") UUID assignmentId,
        @JsonProperty("space_id") UUID spaceId,
        @JsonProperty("space_name") String spaceName,
        String title,
        @JsonProperty("due_at") Instant dueAt
) {
}
