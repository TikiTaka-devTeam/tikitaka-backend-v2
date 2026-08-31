package com.tikitaka.search.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SearchDocumentResponse(
        UUID documentId,
        UUID spaceId,
        String spaceName,
        String title,
        String thumbnailUrl,
        OffsetDateTime uploadedAt) {
}
