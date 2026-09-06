package com.tikitaka.assignment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentUpdateResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("title")
        String title,

        @JsonProperty("description")
        String description,

        @JsonProperty("due_at")
        Instant dueAt,

        @JsonProperty("close_type")
        String closeType,

        @JsonProperty("status")
        String status,

        @JsonProperty("files")
        List<AssignmentFileResponse> files

) {
}