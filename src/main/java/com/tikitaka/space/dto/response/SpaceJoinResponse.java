package com.tikitaka.space.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.SpaceMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceJoinResponse(

        @JsonProperty("space_member_id")
        UUID spaceMemberId,

        @JsonProperty("space_id")
        UUID spaceId,

        @JsonProperty("status")
        @Schema(example = "PENDING")
        SpaceMemberStatus status,

        @JsonProperty("joined_at")
        Instant joinedAt
) {
}