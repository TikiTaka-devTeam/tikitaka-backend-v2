package com.tikitaka.note.dto.response;
import java.util.List;
import java.util.UUID;
public record StrokeLayerResponse(UUID slideId, Integer version, List<StrokeResponse> strokes) {}