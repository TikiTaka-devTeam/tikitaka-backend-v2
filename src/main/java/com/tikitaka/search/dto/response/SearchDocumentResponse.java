package com.tikitaka.search.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SearchDocumentResponse(
        UUID documentId,
        UUID spaceId,
        String spaceName,
        String title,
        String thumbnailUrl,
        Instant uploadedAt) {
}
