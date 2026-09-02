package com.tikitaka.search.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchQuestionResponse(
        UUID questionId,
        UUID spaceId,
        String spaceName,
        String title,
        String contentPreview,
        List<SearchCategoryResponse> categories,
        Instant createdAt) {
}
