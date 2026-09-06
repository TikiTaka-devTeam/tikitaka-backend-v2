package com.tikitaka.assignment.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentSubmissionListResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("max_score")
        BigDecimal maxScore,

        @JsonProperty("grading_status")
        String gradingStatus,

        @JsonProperty("submissions")
        List<AssignmentSubmissionListItemResponse> submissions

) {
}