package com.tikitaka.assignment.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentGradesSaveResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("saved_count")
        long savedCount,

        @JsonProperty("ungraded_count")
        long ungradedCount,

        @JsonProperty("grading_status")
        String gradingStatus

) {
}