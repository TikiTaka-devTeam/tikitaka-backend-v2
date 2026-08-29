package com.tikitaka.space.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpaceUpdateResponse(
        UUID spaceId,
        String spaceName,
        String classroom,
        List<ScheduleResponse> schedules,
        Instant updatedAt
) {
}
