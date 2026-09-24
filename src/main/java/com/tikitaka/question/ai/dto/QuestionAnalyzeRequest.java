package com.tikitaka.question.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record QuestionAnalyzeRequest(

        @JsonProperty("question_id")
        UUID questionId,

        String title,

        String content,

        @JsonProperty("slide_context")
        String slideContext,

        @JsonProperty("document_context")
        String documentContext,

        List<CategoryCandidate> categories
) {

    public record CategoryCandidate(

            @JsonProperty("category_id")
            UUID categoryId,

            String name,

            String description
    ) {
    }
}
