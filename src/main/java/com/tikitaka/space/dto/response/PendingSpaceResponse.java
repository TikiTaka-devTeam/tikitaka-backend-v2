package com.tikitaka.space.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tikitaka.space.entity.SpaceColorKey;
import com.tikitaka.space.entity.SpaceMemberStatus;

public record PendingSpaceResponse(
        UUID spaceMemberId,
        UUID spaceId,
        String spaceName,
        String professorName,
        String classroom,
        List<ScheduleResponse> schedules,
        SpaceColorKey colorKey,
        SpaceMemberStatus status,
        Instant requestedAt
) {
}
