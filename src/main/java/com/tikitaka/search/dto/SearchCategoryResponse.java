package com.tikitaka.search.dto;

import java.util.UUID;

public record SearchCategoryResponse(
        UUID categoryId,
        String categoryName) {
}
