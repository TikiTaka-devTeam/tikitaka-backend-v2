package com.tikitaka.question.ai.dto;

public record AnswerTranscribeResponse(
        String transcript,
        String normalizedContent
) {
}