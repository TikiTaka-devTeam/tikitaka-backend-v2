package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.SpaceMemberRole;

import java.time.Instant;
import java.util.UUID;

public record MemberDetailResponse(

        @JsonProperty("member_id")
        UUID memberId,

        @JsonProperty("name")
        String name,

        @JsonProperty("university")
        String university,

        @JsonProperty("email")
        String email,

        @JsonProperty("major")
        String major,

        @JsonProperty("student_number")
        String studentNumber,

        @JsonProperty("role")
        SpaceMemberRole role,

        @JsonProperty("joined_at")
        Instant joinedAt

) {
}