package com.tikitaka.assignment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentSubmissionListItemResponse(

        @JsonProperty("student_id")
        UUID studentId,

        @JsonProperty("name")
        String name,

        @JsonProperty("student_number")
        String studentNumber,

        @JsonProperty("status")
        String status,

        @JsonProperty("files")
        List<AssignmentFileResponse> files,

        @JsonProperty("submitted_at")
        Instant submittedAt,

        @JsonProperty("score")
        BigDecimal score

) {
}