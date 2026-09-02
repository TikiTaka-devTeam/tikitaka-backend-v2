package com.tikitaka.search.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SearchAnnouncementResponse(
        UUID announcementId,
        UUID spaceId,
        String spaceName,
        String title,
        String contentPreview,
        Instant createdAt) {
}
