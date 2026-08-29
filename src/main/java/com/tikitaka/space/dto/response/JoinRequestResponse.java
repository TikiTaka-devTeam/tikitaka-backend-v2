package com.tikitaka.space.dto.response;

import java.time.Instant;
import java.util.UUID;

public record JoinRequestResponse(
        UUID joinRequestId,
        UUID userId,
        String name,
        String studentNumber,
        String profileUrl,
        Instant requestedAt
) {
}
