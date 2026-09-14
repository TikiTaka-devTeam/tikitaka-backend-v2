package com.tikitaka.question.dto.request;
import java.util.UUID; import com.fasterxml.jackson.annotation.JsonProperty; import jakarta.validation.constraints.NotBlank;
public record CommentCreateRequest(@NotBlank String content, @JsonProperty("parent_comment_id") UUID parentCommentId) {}
