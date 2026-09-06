package com.tikitaka.assignment.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AssignmentGradesRequest(

        @JsonProperty("grades")
        @NotNull
        @NotEmpty
        List<@Valid AssignmentGradeItemRequest> grades

) {
}