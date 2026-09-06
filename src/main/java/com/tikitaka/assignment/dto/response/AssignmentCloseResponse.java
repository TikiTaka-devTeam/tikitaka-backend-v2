package com.tikitaka.assignment.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentCloseResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("status")
        String status

) {
}