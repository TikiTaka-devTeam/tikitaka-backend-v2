package com.tikitaka.note.dto.request;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public record FixerRequest(
        @NotNull @DecimalMin("0") @DecimalMax("1") @JsonProperty("x_ratio") Double xRatio,
        @NotNull @DecimalMin("0") @DecimalMax("1") @JsonProperty("y_ratio") Double yRatio,
        @NotBlank @Size(max = 2000) String content
) {}