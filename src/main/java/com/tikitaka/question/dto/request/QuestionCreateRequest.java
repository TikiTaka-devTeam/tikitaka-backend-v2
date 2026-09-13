package com.tikitaka.question.dto.request;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record QuestionCreateRequest(@NotBlank @Size(max=255) String title, @NotBlank String content,
        @Schema(description = "슬라이드 왼쪽을 0, 오른쪽을 1로 계산한 가로 위치 비율", example = "0.42", minimum = "0", maximum = "1")
        @JsonProperty("x_ratio") @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double xRatio,
        @Schema(description = "슬라이드 위쪽을 0, 아래쪽을 1로 계산한 세로 위치 비율", example = "0.58", minimum = "0", maximum = "1")
        @JsonProperty("y_ratio") @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double yRatio) {}
