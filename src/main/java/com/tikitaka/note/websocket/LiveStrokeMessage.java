package com.tikitaka.note.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.note.entity.StrokeTool;
import java.util.List;
import java.util.UUID;

public record LiveStrokeMessage(
        Type type,
        @JsonProperty("client_stroke_id") UUID clientStrokeId,
        StrokeTool tool,
        String color,
        Double thickness,
        Double opacity,
        @JsonProperty("stroke_order") Integer strokeOrder,
        Point point,
        @JsonProperty("chunk_seq") Integer chunkSeq,
        List<Point> points,
        @JsonProperty("last_chunk_seq") Integer lastChunkSeq
) {
    public enum Type {
        STROKE_START,
        STROKE_POINTS,
        STROKE_END,
        STROKE_CANCEL
    }

    public record Point(
            @JsonProperty("x_ratio") Double xRatio,
            @JsonProperty("y_ratio") Double yRatio
    ) {
    }
}
