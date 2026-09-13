package com.tikitaka.question.controller;

import static com.tikitaka.question.dto.response.QuestionResponses.*;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.question.dto.request.*;
import com.tikitaka.question.service.QuestionService;
import com.tikitaka.question.service.QuestionService.QuestionScope;
import com.tikitaka.question.service.QuestionService.QuestionSortType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Question", description = "질문·답변 API")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class QuestionController {
    private final QuestionService questionService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "QST-001 전체 질문 목록 조회")
    @GetMapping("/api/v1/spaces/{spaceId}/questions")
    public ListResponse getQuestions(@PathVariable UUID spaceId,
            @RequestParam(defaultValue = "LATEST") QuestionSortType sort,
            @RequestParam(name = "document_id", required = false) UUID documentId,
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        return questionService.list(spaceId, sort, documentId, categoryId, cursor, size,
                currentUserResolver.resolve(authentication), false);
    }

    @Operation(summary = "QST-002 내가 등록한 질문 목록 조회")
    @GetMapping("/api/v1/spaces/{spaceId}/questions/mine")
    public ListResponse getMyQuestions(@PathVariable UUID spaceId,
            @RequestParam(defaultValue = "LATEST") QuestionSortType sort,
            @RequestParam(name = "document_id", required = false) UUID documentId,
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        return questionService.list(spaceId, sort, documentId, categoryId, cursor, size,
                currentUserResolver.resolve(authentication), true);
    }

    @Operation(summary = "QST-003 내 질문 요약 조회")
    @GetMapping("/api/v1/spaces/{spaceId}/questions/my-summary")
    public Summary getMyQuestionSummary(@PathVariable UUID spaceId, Authentication authentication) {
        return questionService.summary(spaceId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-004 강의자료 또는 슬라이드 질문 목록 조회")
    @GetMapping("/api/v1/documents/{documentId}/questions")
    public DocumentListResponse getDocumentQuestions(@PathVariable UUID documentId,
            @RequestParam(defaultValue = "ALL") QuestionScope scope,
            @RequestParam(name = "slide_id", required = false) UUID slideId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        return questionService.documentQuestions(documentId, scope, slideId, cursor, size,
                currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-005 질문 상세 조회")
    @GetMapping("/api/v1/questions/{questionId}")
    public Detail getQuestion(@PathVariable UUID questionId, Authentication authentication) {
        return questionService.detail(questionId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-006 슬라이드 핀 위치 질문 등록")
    @PostMapping("/api/v1/slides/{slideId}/questions")
    public Create createPinnedQuestion(@PathVariable UUID slideId,
            @Valid @RequestBody QuestionCreateRequest request, Authentication authentication) {
        return questionService.createPinned(slideId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-007 질문 페이지에서 질문 등록")
    @PostMapping("/api/v1/spaces/{spaceId}/questions")
    public SpaceCreate createQuestion(@PathVariable UUID spaceId,
            @Valid @RequestBody SpaceQuestionCreateRequest request, Authentication authentication) {
        return questionService.create(spaceId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-008 유사 질문 목록 조회")
    @PostMapping("/api/v1/spaces/{spaceId}/questions/similar")
    public SimilarResponse getSimilarQuestions(@PathVariable UUID spaceId,
            @Valid @RequestBody SimilarQuestionRequest request, Authentication authentication) {
        return questionService.similar(spaceId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-009 질문 삭제")
    @DeleteMapping("/api/v1/questions/{questionId}")
    public Delete deleteQuestion(@PathVariable UUID questionId, Authentication authentication) {
        return questionService.deleteQuestion(questionId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-010 공식 답변 등록")
    @PostMapping("/api/v1/questions/{questionId}/answers")
    public AnswerMutation createAnswer(@PathVariable UUID questionId,
            @Valid @RequestBody ContentRequest request, Authentication authentication) {
        return questionService.addAnswer(questionId, request.content(), currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-011 공식 답변 수정")
    @PatchMapping("/api/v1/answers/{answerId}")
    public AnswerMutation updateAnswer(@PathVariable UUID answerId,
            @Valid @RequestBody ContentRequest request, Authentication authentication) {
        return questionService.updateAnswer(answerId, request.content(), currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-012 공식 답변 삭제")
    @DeleteMapping("/api/v1/answers/{answerId}")
    public AnswerMutation deleteAnswer(@PathVariable UUID answerId, Authentication authentication) {
        return questionService.deleteAnswer(answerId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-013 댓글 또는 대댓글 등록")
    @PostMapping("/api/v1/questions/{questionId}/comments")
    public CommentMutation createComment(@PathVariable UUID questionId,
            @Valid @RequestBody CommentCreateRequest request, Authentication authentication) {
        return questionService.addComment(questionId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-014 댓글 또는 대댓글 수정")
    @PatchMapping("/api/v1/question-comments/{commentId}")
    public CommentMutation updateComment(@PathVariable UUID commentId,
            @Valid @RequestBody ContentRequest request, Authentication authentication) {
        return questionService.updateComment(commentId, request.content(), currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-015 댓글 또는 대댓글 삭제")
    @DeleteMapping("/api/v1/question-comments/{commentId}")
    public CommentMutation deleteComment(@PathVariable UUID commentId, Authentication authentication) {
        return questionService.deleteComment(commentId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-016 질문 공감 등록")
    @PostMapping("/api/v1/questions/{questionId}/likes")
    public Like likeQuestion(@PathVariable UUID questionId, Authentication authentication) {
        return questionService.like(questionId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-017 질문 공감 취소")
    @DeleteMapping("/api/v1/questions/{questionId}/likes")
    public Like unlikeQuestion(@PathVariable UUID questionId, Authentication authentication) {
        return questionService.unlike(questionId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-018 강의자료별 질문 카테고리 목록 조회")
    @GetMapping("/api/v1/spaces/{spaceId}/question-categories")
    public CategoriesResponse getQuestionCategories(@PathVariable UUID spaceId,
            Authentication authentication) {
        return questionService.categoryList(spaceId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-019 질문 카테고리 일괄 저장")
    @PatchMapping("/api/v1/spaces/{spaceId}/question-categories")
    public CategoryBatchResponse saveQuestionCategories(@PathVariable UUID spaceId,
            @Valid @RequestBody CategoryBatchRequest request, Authentication authentication) {
        return questionService.saveCategories(spaceId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "QST-020 질문·답변 데이터 CSV 내보내기")
    @GetMapping("/api/v1/spaces/{spaceId}/questions/export")
    public ExportResponse exportQuestions(@PathVariable UUID spaceId,
            @RequestParam(defaultValue = "csv") String format, Authentication authentication) {
        return questionService.export(spaceId, format, currentUserResolver.resolve(authentication));
    }
}
