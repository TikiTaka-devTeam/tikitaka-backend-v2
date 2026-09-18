package com.tikitaka.question.ai.dto;

import java.util.List;
import java.util.UUID;

public record QuestionAnalyzeRequest(
        UUID questionId,
        String title,
        String content,
        String slideContext,
        String documentContext,
        List<CategoryCandidate> categories
) {

    public record CategoryCandidate(
            UUID categoryId,
            String name
    ) {
    }
}