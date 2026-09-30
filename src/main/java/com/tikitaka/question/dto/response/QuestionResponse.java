package com.tikitaka.question.dto.response;
import java.time.Instant; import java.util.List; import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty; import com.tikitaka.question.entity.AnswerType; import com.tikitaka.question.entity.QuestionStatus;
public final class QuestionResponse { private QuestionResponse() {}
 public record DocumentInfo(@JsonProperty("document_id") UUID documentId, String title) {}
 public record SlideInfo(@JsonProperty("slide_id") UUID slideId, @JsonProperty("page_number") Integer pageNumber, @JsonProperty("thumbnail_url") String thumbnailUrl) {}
 public record CategoryInfo(@JsonProperty("category_id") UUID categoryId, String name) {}
 public record AuthorInfo(@JsonProperty("user_id") UUID userId, String name, @JsonProperty("profile_url") String profileUrl) {}
 public record ListItem(@JsonProperty("question_id") UUID questionId, String title, DocumentInfo document, SlideInfo slide,
   List<CategoryInfo> categories, @JsonProperty("created_at") Instant createdAt, @JsonProperty("view_count") Integer viewCount,
   @JsonProperty("like_count") Integer likeCount, boolean liked, QuestionStatus status) {}
 public record ListResponse(List<ListItem> questions, @JsonProperty("total_count") long totalCount,
   @JsonProperty("next_cursor") String nextCursor, @JsonProperty("has_next") boolean hasNext) {}
 public record Summary(@JsonProperty("total_count") long totalCount, @JsonProperty("answered_count") long answeredCount, @JsonProperty("pending_count") long pendingCount) {}
 public record DocumentListItem(@JsonProperty("question_id") UUID questionId, String title, String content, SlideInfo slide,
   List<CategoryInfo> categories, @JsonProperty("x_ratio") Double xRatio, @JsonProperty("y_ratio") Double yRatio,
   @JsonProperty("like_count") Integer likeCount, QuestionStatus status) {}
 public record DocumentListResponse(List<DocumentListItem> questions, @JsonProperty("next_cursor") String nextCursor, @JsonProperty("has_next") boolean hasNext) {}
 public record AnswerInfo(@JsonProperty("answer_id") UUID answerId, AuthorInfo author, String content, @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt, @JsonProperty("answer_type") AnswerType answerType, String transcript) {}
 public record CommentInfo(@JsonProperty("comment_id") UUID commentId, @JsonProperty("parent_comment_id") UUID parentCommentId, AuthorInfo author, @JsonProperty("is_anonymous") boolean isAnonymous, String content, @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt) {}
 public record Detail(@JsonProperty("question_id") UUID questionId, String title, String content, DocumentInfo document, SlideInfo slide,
   List<CategoryInfo> categories, @JsonProperty("x_ratio") Double xRatio, @JsonProperty("y_ratio") Double yRatio,
   @JsonProperty("view_count") Integer viewCount, @JsonProperty("like_count") Integer likeCount, boolean liked,
   QuestionStatus status, List<AnswerInfo> answers, List<CommentInfo> comments) {}
 public record Create(@JsonProperty("question_id") UUID questionId, @JsonProperty("document_id") UUID documentId,
   @JsonProperty("slide_id") UUID slideId, String title, String content, @JsonProperty("x_ratio") Double xRatio,
   @JsonProperty("y_ratio") Double yRatio, List<CategoryInfo> categories, QuestionStatus status, @JsonProperty("created_at") Instant createdAt) {}
 public record SpaceCreate(@JsonProperty("question_id") UUID questionId, DocumentInfo document, SlideInfo slide, String title, String content,
   List<CategoryInfo> categories, QuestionStatus status, @JsonProperty("created_at") Instant createdAt) {}
 public record SimilarItem(@JsonProperty("question_id") UUID questionId, String title, String content, List<CategoryInfo> categories,
   QuestionStatus status, @JsonProperty("like_count") Integer likeCount, boolean liked, double similarity) {}
 public record SimilarResponse(@JsonProperty("question_id") UUID questionId, @JsonProperty("similar_questions") List<SimilarItem> similarQuestions) {}
 public record Delete(@JsonProperty("question_id") UUID questionId, @JsonProperty("is_deleted") boolean deleted, @JsonProperty("deleted_at") Instant deletedAt) {}
 public record AnswerMutation(@JsonProperty("answer_id") UUID answerId, @JsonProperty("question_id") UUID questionId, String content,
   @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt, @JsonProperty("is_deleted") Boolean deleted) {}
 public record CommentMutation(@JsonProperty("comment_id") UUID commentId, @JsonProperty("question_id") UUID questionId,
   @JsonProperty("parent_comment_id") UUID parentCommentId, String content, @JsonProperty("is_anonymous") boolean isAnonymous, @JsonProperty("created_at") Instant createdAt,
   @JsonProperty("updated_at") Instant updatedAt, @JsonProperty("is_deleted") Boolean deleted) {}
 public record Like(@JsonProperty("question_id") UUID questionId, boolean liked, @JsonProperty("like_count") Integer likeCount) {}
 public record CategoryItem(@JsonProperty("category_id") UUID categoryId, String name, String source) {}
 public record DocumentCategories(@JsonProperty("document_id") UUID documentId, String title, List<CategoryItem> categories) {}
 public record CategoriesResponse(List<DocumentCategories> documents) {}
 public record CategoryResult(@JsonProperty("operation_id") String operationId, String type, @JsonProperty("document_id") UUID documentId,
   @JsonProperty("temp_id") String tempId, @JsonProperty("category_id") UUID categoryId, String name, String status) {}
 public record CategoryBatchResponse(List<CategoryResult> results, @JsonProperty("saved_at") Instant savedAt) {}
 public record CategoryMutation(@JsonProperty("category_id") UUID categoryId, @JsonProperty("document_id") UUID documentId, String name, String source) {}
 public record QuestionCategoryMutation(@JsonProperty("question_id") UUID questionId, @JsonProperty("category_id") UUID categoryId,
   @JsonProperty("document_id") UUID documentId, String name, String source) {}
 public record CategorizedQuestion(@JsonProperty("question_id") UUID questionId, String title, String content, QuestionStatus status, @JsonProperty("like_count") Integer likeCount) {}
 public record CategoryGroup(@JsonProperty("category_id") UUID categoryId, String name, List<CategorizedQuestion> questions) {}
 public record CategorizedQuestionsResponse(@JsonProperty("document_id") UUID documentId, List<CategoryGroup> categories) {}
 public record ExportResponse(@JsonProperty("download_url") String downloadUrl) {}
}
