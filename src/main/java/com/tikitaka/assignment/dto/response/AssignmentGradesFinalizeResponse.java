package com.tikitaka.assignment.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentGradesFinalizeResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("grading_status")
        String gradingStatus,

        @JsonProperty("ungraded_count")
        long ungradedCount

) {
}