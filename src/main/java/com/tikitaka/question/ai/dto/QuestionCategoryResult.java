package com.tikitaka.question.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record QuestionCategoryResult(

        @JsonProperty("category_id")
        UUID categoryId,

        double confidence
) {
}