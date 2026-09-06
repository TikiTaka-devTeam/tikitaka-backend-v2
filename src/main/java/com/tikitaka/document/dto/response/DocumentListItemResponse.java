package com.tikitaka.document.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DocumentListItemResponse(
        UUID documentId,
        String title,
        String thumbnailUrl,
        Integer pageCount,
        Instant uploadedAt
) {
}
