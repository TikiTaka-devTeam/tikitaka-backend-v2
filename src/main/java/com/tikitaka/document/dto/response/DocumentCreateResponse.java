package com.tikitaka.document.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DocumentCreateResponse(
        UUID documentId,
        String title,
        String thumbnailUrl,
        Integer pageCount,
        Instant uploadedAt
) {
}
