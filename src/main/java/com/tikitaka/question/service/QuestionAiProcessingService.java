package com.tikitaka.question.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.question.ai.QuestionAiClient;
import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.ai.dto.QuestionAnalyzeResponse;
import com.tikitaka.question.ai.dto.QuestionCategoryResult;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.entity.QuestionScope;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionAiProcessingService {

    private static final int EMBEDDING_DIMENSION =
            768;

    private final QuestionAiClient
            questionAiClient;

    private final QuestionRepository
            questionRepository;

    private final QuestionCategoryRepository
            questionCategoryRepository;

    private final QuestionCategoryMappingRepository
            questionCategoryMappingRepository;

    private final QuestionAiContextService
            questionAiContextService;

    private final TransactionTemplate
            transactionTemplate;

    @Value("${question.ai.category-confidence-threshold:0.75}")
    private double categoryConfidenceThreshold;

    public void process(
            UUID questionId,
            String slideContext,
            String documentContext
    ) {

        AnalysisContext context =
                markProcessingAndLoadContext(
                        questionId
                );

        try {

            ResolvedContext resolvedContext =
                    resolveContext(
                            context,
                            slideContext,
                            documentContext
                    );

            QuestionAnalyzeResponse response =
                    analyze(
                            context,
                            resolvedContext
                                    .slideContext(),
                            resolvedContext
                                    .documentContext()
                    );

            if (response.isOther()) {

                transactionTemplate
                        .executeWithoutResult(
                                status ->
                                        applyOtherResult(
                                                questionId
                                        )
                        );

                return;
            }

            transactionTemplate
                    .executeWithoutResult(
                            status ->
                                    applyCourseRelatedResult(
                                            questionId,
                                            response
                                    )
                    );

        } catch (Exception exception) {

            markFailed(
                    questionId
            );

            throw exception;
        }
    }

    private AnalysisContext
    markProcessingAndLoadContext(
            UUID questionId
    ) {

        AnalysisContext context =
                transactionTemplate.execute(
                        status -> {

                            Question question =
                                    getQuestion(
                                            questionId
                                    );

                            question
                                    .startAiProcessing();

                            List<
                                    QuestionAnalyzeRequest
                                            .CategoryCandidate
                                    > candidates =
                                    questionCategoryRepository
                                            .findAllByDocumentIdAndDeletedFalse(
                                                    question
                                                            .getDocument()
                                                            .getId()
                                            )
                                            .stream()
                                            .filter(category ->
                                                    !category.getName().equalsIgnoreCase(
                                                            QuestionCategory.FALLBACK_NAME
                                                    )
                                            )
                                            .map(
                                                    category ->
                                                            new QuestionAnalyzeRequest
                                                                    .CategoryCandidate(
                                                                    category
                                                                            .getId(),
                                                                    category
                                                                            .getName(),
                                                                    category
                                                                            .getDescription()
                                                            )
                                            )
                                            .toList();

                            UUID slideId =
                                    question
                                                    .getSlide()
                                            == null
                                            ? null
                                            : question
                                                    .getSlide()
                                                    .getId();

                            return new AnalysisContext(
                                    question.getId(),
                                    question
                                            .getDocument()
                                            .getId(),
                                    slideId,
                                    question.getTitle(),
                                    question.getContent(),
                                    candidates
                            );
                        }
                );

        if (context == null) {

            throw new IllegalStateException(
                    "Question processing transaction returned null."
            );
        }

        return context;
    }

    private ResolvedContext resolveContext(
            AnalysisContext context,
            String slideContext,
            String documentContext
    ) {

        boolean needsSlideContext =
                context.slideId() != null
                        && isBlank(
                        slideContext
                );

        boolean needsDocumentContext =
                isBlank(
                        documentContext
                );

        if (!needsSlideContext
                && !needsDocumentContext) {

            return new ResolvedContext(
                    normalizeNullable(
                            slideContext
                    ),
                    normalizeNullable(
                            documentContext
                    )
            );
        }

        QuestionAiContextService.Context
                loaded =
                questionAiContextService
                        .load(
                                context.documentId(),
                                context.slideId()
                        );

        return new ResolvedContext(

                needsSlideContext
                        ? loaded.slideContext()
                        : normalizeNullable(
                        slideContext
                ),

                needsDocumentContext
                        ? loaded.documentContext()
                        : normalizeNullable(
                        documentContext
                )
        );
    }

    public QuestionAnalyzeResponse analyze(
            AnalysisContext context,
            String slideContext,
            String documentContext
    ) {

        QuestionAnalyzeRequest request =
                new QuestionAnalyzeRequest(
                        context.questionId(),
                        context.title(),
                        context.content(),
                        slideContext,
                        documentContext,
                        context.categoryCandidates()
                );

        QuestionAnalyzeResponse response =
                questionAiClient
                        .analyzeQuestion(
                                request
                        );

        validateResponse(
                response
        );

        return response;
    }

    private void applyOtherResult(
            UUID questionId
    ) {

        Question question =
                getQuestion(
                        questionId
                );

        questionCategoryMappingRepository
                .deleteAllByQuestionId(
                        questionId
                );

        question
                .completeOtherProcessing();
    }

    private void applyCourseRelatedResult(
            UUID questionId,
            QuestionAnalyzeResponse response
    ) {

        Question question =
                getQuestion(
                        questionId
                );

        List<QuestionCategory>
                selectedCategories =
                getSelectedCategories(
                        question,
                        response.categories()
                );

        if (selectedCategories.isEmpty()) {
            QuestionCategory fallbackCategory =
                    questionCategoryRepository
                            .findByDocumentIdAndNameAndDeletedFalse(
                                    question.getDocument().getId(),
                                    QuestionCategory.FALLBACK_NAME
                            )
                            .orElseGet(() -> questionCategoryRepository.save(
                                    QuestionCategory.createFallback(
                                            question.getDocument()
                                    )
                            ));
            selectedCategories = List.of(fallbackCategory);
        }

        questionCategoryMappingRepository
                .deleteAllByQuestionId(
                        questionId
                );

        List<QuestionCategoryMapping>
                mappings =
                selectedCategories
                        .stream()
                        .map(
                                category ->
                                        QuestionCategoryMapping
                                                .create(
                                                        question,
                                                        category
                                                )
                        )
                        .toList();

        questionCategoryMappingRepository
                .saveAll(
                        mappings
                );

        question
                .completeCourseRelatedProcessing(
                        response.embedding()
                );
    }

    private List<QuestionCategory>
    getSelectedCategories(
            Question question,
            List<QuestionCategoryResult>
                    categoryResults
    ) {

        List<QuestionCategory> categories =
                new ArrayList<>();

        if (categoryResults == null || categoryResults.isEmpty()) {
            return categories;
        }

        for (
                QuestionCategoryResult result
                : categoryResults
        ) {

            if (result.confidence()
                    < categoryConfidenceThreshold) {

                continue;
            }

            QuestionCategory category =
                    questionCategoryRepository
                            .findById(
                                    result.categoryId()
                            )
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Category not found: "
                                                            + result.categoryId()
                                            )
                            );

            validateUsableCategory(
                    question,
                    category
            );

            if (category.getName().equalsIgnoreCase(
                    QuestionCategory.FALLBACK_NAME
            )) {
                continue;
            }

            categories.add(
                    category
            );
        }

        return categories;
    }

    private void validateUsableCategory(
            Question question,
            QuestionCategory category
    ) {

        if (!question
                .getDocument()
                .getId()
                .equals(
                        category
                                .getDocument()
                                .getId()
                )) {

            throw new IllegalStateException(
                    "Question and category must belong to the same document."
            );
        }

        if (category.isDeleted()) {

            throw new IllegalStateException(
                    "Deleted category cannot be used: "
                            + category.getId()
            );
        }
    }

    private void validateResponse(
            QuestionAnalyzeResponse response
    ) {

        if (response == null
                || response.relation()
                == null) {

            throw new IllegalStateException(
                    "AI question response/relation must not be null."
            );
        }

        if (response.relation()
                == QuestionScope.OTHER) {

            if (response.embedding()
                    != null) {

                throw new IllegalStateException(
                        "OTHER question must not have embedding."
                );
            }

            if (response.categories()
                    != null
                    && !response
                    .categories()
                    .isEmpty()) {

                throw new IllegalStateException(
                        "OTHER question must not have categories."
                );
            }

            return;
        }

        if (response.relation()
                != QuestionScope.COURSE_RELATED) {

            throw new IllegalStateException(
                    "Unsupported AI relation: "
                            + response.relation()
            );
        }

        if (response.embedding()
                == null
                || response
                .embedding()
                .length
                != EMBEDDING_DIMENSION) {

            throw new IllegalStateException(
                    "COURSE_RELATED embedding dimension must be "
                            + EMBEDDING_DIMENSION
                            + "."
            );
        }

        // An empty category result falls back to the document's "기타" category.
    }

    private Question getQuestion(
            UUID questionId
    ) {

        return questionRepository
                .findById(
                        questionId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Question not found: "
                                                + questionId
                                )
                );
    }

    private void markFailed(
            UUID questionId
    ) {

        transactionTemplate
                .executeWithoutResult(
                        status ->
                                questionRepository
                                        .findById(
                                                questionId
                                        )
                                        .ifPresent(
                                                Question::
                                                        failAiProcessing
                                        )
                );
    }

    private boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }

    private String normalizeNullable(
            String value
    ) {
        return isBlank(value)
                ? null
                : value.trim();
    }

    public record AnalysisContext(
            UUID questionId,
            UUID documentId,
            UUID slideId,
            String title,
            String content,
            List<
                    QuestionAnalyzeRequest
                            .CategoryCandidate
                    > categoryCandidates
    ) {
    }

    private record ResolvedContext(
            String slideContext,
            String documentContext
    ) {
    }
}
