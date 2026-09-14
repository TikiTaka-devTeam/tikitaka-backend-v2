package com.tikitaka.question.dto.request;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size;
public record SpaceQuestionCreateRequest(@JsonProperty("document_id") @NotNull UUID documentId,
        @NotBlank @Size(max=255) String title, @NotBlank String content) {}
