package com.tikitaka.assignment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentDetailResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("title")
        String title,

        @JsonProperty("description")
        String description,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("writer_name")
        String writerName,

        @JsonProperty("view_count")
        Integer viewCount,

        @JsonProperty("files")
        List<AssignmentFileResponse> files,

        @JsonProperty("due_at")
        Instant dueAt,

        @JsonProperty("status")
        String status,

        @JsonProperty("my_submission")
        AssignmentSubmissionResponse mySubmission,

        @JsonProperty("grading_status")
        String gradingStatus,

        @JsonProperty("score")
        BigDecimal score,

        @JsonProperty("max_score")
        BigDecimal maxScore
) {
}