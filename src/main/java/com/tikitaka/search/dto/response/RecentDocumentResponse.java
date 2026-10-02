package com.tikitaka.search.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RecentDocumentResponse(
        UUID documentId, UUID spaceId, String spaceName, String title, Instant viewedAt,
        String thumbnailUrl) {
}
