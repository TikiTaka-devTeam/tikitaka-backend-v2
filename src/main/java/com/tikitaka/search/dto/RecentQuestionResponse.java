package com.tikitaka.search.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentQuestionResponse(
        UUID questionId, UUID spaceId, String spaceName, String title, Instant viewedAt) {
}
