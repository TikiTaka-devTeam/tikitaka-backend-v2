package com.tikitaka.assignment.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentDeleteResponse(

        @JsonProperty("assignment_id")
        UUID assignmentId,

        @JsonProperty("is_deleted")
        boolean deleted

) {
}