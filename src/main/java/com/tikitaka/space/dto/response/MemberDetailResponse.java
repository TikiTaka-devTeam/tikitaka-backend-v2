package com.tikitaka.space.dto.response;

import com.tikitaka.space.entity.SpaceMemberRole;

import java.time.Instant;
import java.util.UUID;

public record MemberDetailResponse(
        UUID memberId,
        String name,
        String university,
        String email,
        String major,
        String studentNumber,
        SpaceMemberRole role,
        Instant joinedAt
) {
}
