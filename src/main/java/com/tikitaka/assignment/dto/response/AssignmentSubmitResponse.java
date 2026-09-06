package com.tikitaka.assignment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentSubmitResponse(

        @JsonProperty("submission_id")
        UUID submissionId,

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("comment")
        String comment,

        @JsonProperty("files")
        List<AssignmentFileResponse> files,

        @JsonProperty("version")
        Integer version,

        @JsonProperty("status")
        String status,

        @JsonProperty("submitted_at")
        Instant submittedAt

) {
}