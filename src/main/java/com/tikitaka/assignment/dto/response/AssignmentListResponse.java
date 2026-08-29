package com.tikitaka.assignment.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentListResponse(

        @JsonProperty("assignments")
        List<AssignmentListItemResponse> assignments

) {
}