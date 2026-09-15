package com.tikitaka.note.dto.response;
import java.util.List;
import java.util.UUID;
public record StrokeSyncResponse(UUID slideId, Integer version, int appliedCount, List<CreatedStroke> createdStrokes) {
    public record CreatedStroke(UUID clientStrokeId, UUID strokeId) {}
}