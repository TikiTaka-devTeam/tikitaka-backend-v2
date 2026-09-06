package com.tikitaka.assignment.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AssignmentMaxScoreRequest(

        @JsonProperty("max_score")
        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal maxScore

) {
}