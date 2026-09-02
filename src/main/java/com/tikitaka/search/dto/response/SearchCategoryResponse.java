package com.tikitaka.search.dto.response;

import java.util.UUID;

public record SearchCategoryResponse(
        UUID categoryId,
        String categoryName) {
}
