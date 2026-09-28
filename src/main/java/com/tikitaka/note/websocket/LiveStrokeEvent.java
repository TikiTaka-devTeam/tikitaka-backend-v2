package com.tikitaka.note.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.note.entity.StrokeTool;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveStrokeEvent(
        LiveStrokeMessage.Type type,
        @JsonProperty("space_id") UUID spaceId,
        @JsonProperty("slide_id") UUID slideId,
        @JsonProperty("client_stroke_id") UUID clientStrokeId,
        StrokeTool tool,
        String color,
        Double thickness,
        Double opacity,
        @JsonProperty("stroke_order") Integer strokeOrder,
        LiveStrokeMessage.Point point,
        @JsonProperty("chunk_seq") Integer chunkSeq,
        List<LiveStrokeMessage.Point> points,
        @JsonProperty("last_chunk_seq") Integer lastChunkSeq
) {
    public static LiveStrokeEvent from(UUID spaceId, UUID slideId, LiveStrokeMessage message) {
        return new LiveStrokeEvent(message.type(), spaceId, slideId, message.clientStrokeId(),
                message.tool(), message.color(), message.thickness(), message.opacity(),
                message.strokeOrder(), message.point(), message.chunkSeq(), message.points(),
                message.lastChunkSeq());
    }
}
