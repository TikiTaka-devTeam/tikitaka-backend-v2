package com.tikitaka.assignment.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AssignmentMaxScoreRequest(

        @JsonProperty("max_score")
        @Schema(
                description = "과제 만점",
                example = "100"
        )
        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal maxScore

) {
}