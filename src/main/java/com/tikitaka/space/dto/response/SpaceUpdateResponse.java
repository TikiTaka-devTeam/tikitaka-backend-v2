package com.tikitaka.space.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpaceUpdateResponse(

        @JsonProperty("space_id")
        UUID spaceId,

        @JsonProperty("space_name")
        String spaceName,

        @JsonProperty("classroom")
        String classroom,

        @JsonProperty("schedules")
        List<ScheduleResponse> schedules,

        @JsonProperty("updated_at")
        Instant updatedAt
) {
}