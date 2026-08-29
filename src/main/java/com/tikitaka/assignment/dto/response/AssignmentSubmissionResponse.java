package com.tikitaka.assignment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentSubmissionResponse(

        @JsonProperty("submission_id")
        UUID submissionId,

        @JsonProperty("comment")
        String comment,

        @JsonProperty("files")
        List<AssignmentFileResponse> files,

        @JsonProperty("status")
        String status,

        @JsonProperty("submitted_at")
        Instant submittedAt

) {
}