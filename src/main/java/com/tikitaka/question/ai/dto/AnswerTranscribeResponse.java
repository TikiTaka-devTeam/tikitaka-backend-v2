package com.tikitaka.question.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnswerTranscribeResponse(

        String transcript,

        @JsonProperty("normalized_content")
        String normalizedContent
) {
}