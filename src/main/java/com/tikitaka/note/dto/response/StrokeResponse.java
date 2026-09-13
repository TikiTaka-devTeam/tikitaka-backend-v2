package com.tikitaka.note.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.note.entity.PrivateStroke;
import com.tikitaka.note.entity.SharedStroke;
import com.tikitaka.note.entity.StrokeTool;

public record StrokeResponse(
        UUID strokeId, StrokeTool tool, List<Map<String, Double>> points,
        String color, Double thickness, Double opacity, Integer strokeOrder,
        @JsonProperty("is_deleted") boolean deleted
) {
    public static StrokeResponse of(PrivateStroke s) {
        return new StrokeResponse(s.getId(), s.getTool(), s.getPoints(), s.getColor(),
                s.getThickness(), s.getOpacity(), s.getStrokeOrder(), s.isDeleted());
    }
    public static StrokeResponse of(SharedStroke s) {
        return new StrokeResponse(s.getId(), s.getTool(), s.getPoints(), s.getColor(),
                s.getThickness(), s.getOpacity(), s.getStrokeOrder(), s.isDeleted());
    }
}