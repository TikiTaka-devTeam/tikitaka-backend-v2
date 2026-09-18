package com.tikitaka.question.ai.dto;

import java.util.UUID;

public record QuestionCategoryResult(
        UUID categoryId,
        double confidence
) {
}