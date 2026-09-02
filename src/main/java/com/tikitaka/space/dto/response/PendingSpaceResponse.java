package com.tikitaka.space.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.SpaceColorKey;
import com.tikitaka.space.entity.SpaceMemberStatus;

public record PendingSpaceResponse(

        @JsonProperty("space_member_id")
        UUID spaceMemberId,

        @JsonProperty("space_id")
        UUID spaceId,

        @JsonProperty("space_name")
        String spaceName,

        @JsonProperty("professor_name")
        String professorName,

        @JsonProperty("year")
        Integer year,

        @JsonProperty("semester")
        String semester,

        @JsonProperty("classroom")
        String classroom,

        @JsonProperty("schedules")
        List<ScheduleResponse> schedules,

        @JsonProperty("color_key")
        SpaceColorKey colorKey,

        @JsonProperty("status")
        SpaceMemberStatus status,

        @JsonProperty("requested_at")
        OffsetDateTime requestedAt
) {
}
