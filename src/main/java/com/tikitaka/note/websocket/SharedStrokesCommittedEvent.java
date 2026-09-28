package com.tikitaka.note.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.note.dto.request.StrokeSyncRequest;
import com.tikitaka.note.entity.StrokeTool;
import java.util.List;
import java.util.UUID;

public record SharedStrokesCommittedEvent(
        UUID spaceId,
        UUID slideId,
        int version,
        List<CreatedStroke> createdStrokes,
        List<UUID> deletedStrokeIds
) {
    public record CreatedStroke(
            @JsonProperty("client_stroke_id") UUID clientStrokeId,
            @JsonProperty("stroke_id") UUID strokeId,
            StrokeTool tool,
            List<StrokeSyncRequest.Point> points,
            String color,
            Double thickness,
            Double opacity,
            @JsonProperty("stroke_order") Integer strokeOrder
    ) {
        public static CreatedStroke of(StrokeSyncRequest.Stroke stroke, UUID strokeId) {
            return new CreatedStroke(stroke.clientStrokeId(), strokeId, stroke.tool(),
                    stroke.points(), stroke.color(), stroke.thickness(), stroke.opacity(),
                    stroke.strokeOrder());
        }
    }
}
