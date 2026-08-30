package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.space.entity.SpaceMemberRole;

import java.util.UUID;

public record MemberListItemResponse(

        @JsonProperty("member_id")
        UUID memberId,

        @JsonProperty("role")
        SpaceMemberRole role,

        @JsonProperty("name")
        String name,

        @JsonProperty("student_number")
        String studentNumber,

        @JsonProperty("profile_url")
        String profileUrl

) {
}