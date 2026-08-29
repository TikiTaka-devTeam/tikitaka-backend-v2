package com.tikitaka.space.dto.response;

import com.tikitaka.space.entity.SpaceMemberRole;

import java.util.UUID;

public record MemberListItemResponse(
        UUID memberId,
        SpaceMemberRole role,
        String name,
        String studentNumber,
        String profileUrl
) {
}
