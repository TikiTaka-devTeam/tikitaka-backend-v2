package com.tikitaka.question.dto.request;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record QuestionCreateRequest(@NotBlank @Size(max=255) String title, @NotBlank String content,
        @JsonProperty("x_ratio") @NotNull Double xRatio, @JsonProperty("y_ratio") @NotNull Double yRatio) {}
