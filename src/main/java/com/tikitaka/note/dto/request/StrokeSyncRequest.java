package com.tikitaka.note.dto.request;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.note.entity.StrokeTool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record StrokeSyncRequest(
        @NotNull @Min(0) @JsonProperty("base_version") Integer baseVersion,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Operation> operations
) {
    public enum Type { CREATE, DELETE }

    public record Operation(
            @NotNull @JsonProperty("client_operation_id") UUID clientOperationId,
            @NotNull Type type,
            @Valid Stroke stroke,
            @JsonProperty("stroke_id") UUID strokeId
    ) {
        public Payload payload() { return new Payload(type, stroke, strokeId); }
    }

    public record Payload(Type type, Stroke stroke, UUID strokeId) {}

    public record Stroke(
            @NotNull @JsonProperty("client_stroke_id") UUID clientStrokeId,
            @NotNull StrokeTool tool,
            @NotNull @Size(min = 1, max = 5000) List<@NotNull @Valid Point> points,
            @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
            @NotNull @DecimalMin(value = "0", inclusive = false) Double thickness,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double opacity,
            @NotNull @Min(0) @JsonProperty("stroke_order") Integer strokeOrder
    ) {}

    public record Point(
            @NotNull @DecimalMin("0") @DecimalMax("1") @JsonProperty("x_ratio") Double xRatio,
            @NotNull @DecimalMin("0") @DecimalMax("1") @JsonProperty("y_ratio") Double yRatio
    ) {}
}