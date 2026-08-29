package com.tikitaka.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentListItemResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("title")
        String title,

        @JsonProperty("content_preview")
        String contentPreview,

        @JsonProperty("due_at")
        Instant dueAt,

        @JsonProperty("status")
        String status,

        @JsonProperty("submission_status")
        String submissionStatus
) {
}