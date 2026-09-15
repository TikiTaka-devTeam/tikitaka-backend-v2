package com.tikitaka.question.dto.request;
import java.util.UUID; import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
public record SimilarQuestionRequest(@JsonProperty("document_id") @NotNull UUID documentId,
        @JsonProperty("slide_id") UUID slideId, @NotBlank String title, @NotBlank String content) {}
