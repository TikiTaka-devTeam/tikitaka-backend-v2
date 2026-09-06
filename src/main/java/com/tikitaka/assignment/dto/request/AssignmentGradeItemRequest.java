package com.tikitaka.assignment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AssignmentGradeItemRequest(

        @JsonProperty("student_id")
        @NotNull
        UUID studentId,

        @JsonProperty("score")
        @DecimalMin(value = "0.0")
        BigDecimal score

) {
}