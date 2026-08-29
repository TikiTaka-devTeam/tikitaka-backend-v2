package com.tikitaka.assignment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentSummaryResponse(

        @JsonProperty("not_submitted_count")
        Long notSubmittedCount,

        @JsonProperty("before_deadline_count")
        Long beforeDeadlineCount,

        @JsonProperty("grading_pending_count")
        Long gradingPendingCount
) {

    public static AssignmentSummaryResponse forStudent(
            long notSubmittedCount
    ) {
        return new AssignmentSummaryResponse(
                notSubmittedCount,
                null,
                null
        );
    }

    public static AssignmentSummaryResponse forManager(
            long beforeDeadlineCount,
            long gradingPendingCount
    ) {
        return new AssignmentSummaryResponse(
                null,
                beforeDeadlineCount,
                gradingPendingCount
        );
    }
}