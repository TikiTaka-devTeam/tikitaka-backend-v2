package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record JoinRequestResponse(

        @JsonProperty("join_request_id")
        UUID joinRequestId,

        @JsonProperty("user_id")
        UUID userId,

        @JsonProperty("name")
        String name,

        @JsonProperty("student_number")
        String studentNumber,

        @JsonProperty("profile_url")
        String profileUrl,

        @JsonProperty("requested_at")
        Instant requestedAt

) {
}