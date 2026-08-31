package com.tikitaka.search.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SearchAnnouncementResponse(
        UUID announcementId,
        UUID spaceId,
        String spaceName,
        String title,
        String contentPreview,
        OffsetDateTime createdAt) {
}
