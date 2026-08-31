package com.tikitaka.search.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SearchQuestionResponse(
        UUID questionId,
        UUID spaceId,
        String spaceName,
        String title,
        String contentPreview,
        List<SearchCategoryResponse> categories,
        OffsetDateTime createdAt) {
}
