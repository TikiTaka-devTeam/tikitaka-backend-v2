package com.tikitaka.assignment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentGradeUpdateResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("student_id")
        UUID studentId,

        @JsonProperty("score")
        BigDecimal score,

        @JsonProperty("grading_status")
        String gradingStatus

) {
}