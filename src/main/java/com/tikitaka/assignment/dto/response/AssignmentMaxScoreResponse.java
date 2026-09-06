package com.tikitaka.assignment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentMaxScoreResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("max_score")
        BigDecimal maxScore

) {
}