package com.tikitaka.space.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.space.entity.SpaceMemberStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceJoinResponse(
        UUID spaceMemberId,
        UUID spaceId,
        @Schema(example = "PENDING") SpaceMemberStatus status,
        Instant joinedAt
) {
}
