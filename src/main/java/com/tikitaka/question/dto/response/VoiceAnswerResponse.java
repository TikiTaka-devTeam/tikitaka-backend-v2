package com.tikitaka.question.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.question.entity.AnswerType;

public record VoiceAnswerResponse(

        @JsonProperty("answer_id")
        UUID answerId,

        @JsonProperty("question_id")
        UUID questionId,

        @JsonProperty("answer_type")
        AnswerType answerType,

        String content,

        String transcript,

        @JsonProperty("audio_url")
        String audioUrl,

        @JsonProperty("created_at")
        Instant createdAt
) {
}