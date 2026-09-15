package com.tikitaka.question.dto.request;
import java.util.List; import java.util.UUID; import com.fasterxml.jackson.annotation.JsonProperty; import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotEmpty; import jakarta.validation.constraints.NotNull;
public record CategoryBatchRequest(@NotEmpty List<@Valid Operation> operations) {
 public record Operation(@JsonProperty("operation_id") @NotBlank String operationId, @NotNull Type type,
   @JsonProperty("document_id") @NotNull UUID documentId, @JsonProperty("temp_id") String tempId,
   @JsonProperty("category_id") UUID categoryId, String name) {}
 public enum Type { CREATE, UPDATE, DELETE }
}
