package com.tikitaka.question.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.question.ai.ClusterAiClient;
import com.tikitaka.question.ai.QuestionAiClient;
import com.tikitaka.question.ai.dto.ClusterTitleRequest;
import com.tikitaka.question.ai.dto.ClusterTitleResponse;
import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.ai.dto.QuestionAnalyzeResponse;
import com.tikitaka.question.ai.dto.QuestionCategoryResult;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.entity.QuestionCluster;
import com.tikitaka.question.entity.QuestionScope;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionClusterRepository;
import com.tikitaka.question.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionAiProcessingService {

    private static final int EMBEDDING_DIMENSION = 768;

    private final QuestionAiClient questionAiClient;
    private final ClusterAiClient clusterAiClient;

    private final QuestionRepository questionRepository;
    private final QuestionCategoryRepository questionCategoryRepository;
    private final QuestionCategoryMappingRepository questionCategoryMappingRepository;
    private final QuestionClusterRepository questionClusterRepository;

    private final QuestionClusterService questionClusterService;
    private final QuestionAiContextService questionAiContextService;

    private final TransactionTemplate transactionTemplate;

    public void process(
            UUID questionId,
            String slideContext,
            String documentContext
    ) {
        AnalysisContext context =
                markProcessingAndLoadContext(questionId);

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
                            resolvedContext.slideContext(),
                            resolvedContext.documentContext()
                    );

            if (response.isOther()) {
                transactionTemplate.executeWithoutResult(status ->
                        applyOtherResult(questionId)
                );
                return;
            }

            Optional<UUID> existingClusterId =
                    questionClusterService
                            .findMatchingCluster(
                                    context.documentId(),
                                    response.primaryCategoryId(),
                                    response.embedding()
                            )
                            .map(QuestionCluster::getId);

            if (existingClusterId.isPresent()) {
                UUID clusterId = existingClusterId.get();

                transactionTemplate.executeWithoutResult(status ->
                        applyCourseRelatedResult(
                                questionId,
                                response,
                                clusterId,
                                null
                        )
                );
                return;
            }

            String categoryName =
                    findCategoryName(
                            context.categoryCandidates(),
                            response.primaryCategoryId()
                    );

            ClusterTitleResponse titleResponse =
                    clusterAiClient.generateTitle(
                            new ClusterTitleRequest(
                                    context.title(),
                                    context.content(),
                                    categoryName
                            )
                    );

            transactionTemplate.executeWithoutResult(status ->
                    applyCourseRelatedResult(
                            questionId,
                            response,
                            null,
                            titleResponse.summaryTitle()
                    )
            );

        } catch (Exception exception) {
            markFailed(questionId);
            throw exception;
        }
    }

    private AnalysisContext markProcessingAndLoadContext(
            UUID questionId
    ) {
        AnalysisContext context =
                transactionTemplate.execute(status -> {
                    Question question =
                            questionRepository.findById(questionId)
                                    .orElseThrow(() ->
                                            new IllegalArgumentException(
                                                    "Question not found: "
                                                            + questionId
                                            )
                                    );

                    question.startAiProcessing();

                    List<QuestionAnalyzeRequest.CategoryCandidate> candidates =
                            questionCategoryRepository
                                    .findAllByDocumentIdAndDeletedFalse(
                                            question.getDocument().getId()
                                    )
                                    .stream()
                                    .map(category ->
                                            new QuestionAnalyzeRequest.CategoryCandidate(
                                                    category.getId(),
                                                    category.getName()
                                            )
                                    )
                                    .toList();

                    UUID slideId =
                            question.getSlide() == null
                                    ? null
                                    : question.getSlide().getId();

                    return new AnalysisContext(
                            question.getId(),
                            question.getDocument().getId(),
                            slideId,
                            question.getTitle(),
                            question.getContent(),
                            candidates
                    );
                });

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
                        && isBlank(slideContext);

        boolean needsDocumentContext =
                isBlank(documentContext);

        if (!needsSlideContext && !needsDocumentContext) {
            return new ResolvedContext(
                    normalizeNullable(slideContext),
                    normalizeNullable(documentContext)
            );
        }

        QuestionAiContextService.Context loaded =
                questionAiContextService.load(
                        context.documentId(),
                        context.slideId()
                );

        return new ResolvedContext(
                needsSlideContext
                        ? loaded.slideContext()
                        : normalizeNullable(slideContext),
                needsDocumentContext
                        ? loaded.documentContext()
                        : normalizeNullable(documentContext)
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
                questionAiClient.analyzeQuestion(request);

        validateResponse(response);

        return response;
    }

    private void applyOtherResult(
            UUID questionId
    ) {
        Question question = getQuestion(questionId);

        questionCategoryMappingRepository
                .deleteAllByQuestionId(questionId);

        question.completeOtherProcessing();
    }

    private void applyCourseRelatedResult(
            UUID questionId,
            QuestionAnalyzeResponse response,
            UUID existingClusterId,
            String newClusterTitle
    ) {
        Question question = getQuestion(questionId);

        QuestionCategory primaryCategory =
                existingClusterId == null
                        ? getPrimaryCategoryForUpdate(
                                question,
                                response.primaryCategoryId()
                        )
                        : getPrimaryCategory(
                                question,
                                response.primaryCategoryId()
                        );

        List<QuestionCategory> selectedCategories =
                getSelectedCategories(
                        question,
                        response.categories()
                );

        questionCategoryMappingRepository
                .deleteAllByQuestionId(questionId);

        saveCategoryMappings(
                question,
                selectedCategories
        );

        question.completeCourseRelatedProcessing(
                primaryCategory,
                response.embedding()
        );

        if (existingClusterId != null) {
            QuestionCluster cluster =
                    getClusterForUpdate(existingClusterId);

            questionClusterService.assignToExistingCluster(
                    cluster,
                    question,
                    response.embedding()
            );
            return;
        }

        // 신규 Cluster 제목을 생성하는 동안 다른 요청이 동일 Category에
        // Cluster를 먼저 만들었을 수 있다. Category row lock을 획득한 상태에서
        // 다시 검색하여 중복 Cluster 생성을 방지한다.
        Optional<QuestionCluster> recheckedCluster =
                questionClusterService.findMatchingCluster(
                        question.getDocument().getId(),
                        primaryCategory.getId(),
                        response.embedding()
                );

        if (recheckedCluster.isPresent()) {
            QuestionCluster cluster =
                    getClusterForUpdate(
                            recheckedCluster.get().getId()
                    );

            questionClusterService.assignToExistingCluster(
                    cluster,
                    question,
                    response.embedding()
            );
            return;
        }

        if (newClusterTitle == null
                || newClusterTitle.isBlank()) {
            throw new IllegalStateException(
                    "New cluster title must not be empty."
            );
        }

        questionClusterService.createNewCluster(
                question.getDocument(),
                primaryCategory,
                question,
                newClusterTitle,
                response.embedding()
        );
    }

    private Question getQuestion(
            UUID questionId
    ) {
        return questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Question not found: " + questionId
                        )
                );
    }

    private QuestionCategory getPrimaryCategory(
            Question question,
            UUID primaryCategoryId
    ) {
        QuestionCategory category =
                questionCategoryRepository.findById(primaryCategoryId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Primary category not found: "
                                                + primaryCategoryId
                                )
                        );

        validateUsableCategory(question, category);
        return category;
    }

    private QuestionCategory getPrimaryCategoryForUpdate(
            Question question,
            UUID primaryCategoryId
    ) {
        QuestionCategory category =
                questionCategoryRepository
                        .findQuestionCategoryById(primaryCategoryId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Primary category not found: "
                                                + primaryCategoryId
                                )
                        );

        validateUsableCategory(question, category);
        return category;
    }

    private QuestionCluster getClusterForUpdate(
            UUID clusterId
    ) {
        return questionClusterRepository
                .findQuestionClusterById(clusterId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Question cluster not found: "
                                        + clusterId
                        )
                );
    }

    private List<QuestionCategory> getSelectedCategories(
            Question question,
            List<QuestionCategoryResult> categoryResults
    ) {
        List<QuestionCategory> categories = new ArrayList<>();

        for (QuestionCategoryResult result : categoryResults) {
            QuestionCategory category =
                    questionCategoryRepository
                            .findById(result.categoryId())
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Category not found: "
                                                    + result.categoryId()
                                    )
                            );

            validateUsableCategory(question, category);
            categories.add(category);
        }

        return categories;
    }

    private void saveCategoryMappings(
            Question question,
            List<QuestionCategory> categories
    ) {
        List<QuestionCategoryMapping> mappings =
                categories.stream()
                        .map(category ->
                                QuestionCategoryMapping.create(
                                        question,
                                        category
                                )
                        )
                        .toList();

        questionCategoryMappingRepository.saveAll(mappings);
    }

    private String findCategoryName(
            List<QuestionAnalyzeRequest.CategoryCandidate> candidates,
            UUID categoryId
    ) {
        return candidates.stream()
                .filter(candidate ->
                        candidate.categoryId().equals(categoryId)
                )
                .map(QuestionAnalyzeRequest.CategoryCandidate::name)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Primary category does not exist in analysis context: "
                                        + categoryId
                        )
                );
    }

    private void validateUsableCategory(
            Question question,
            QuestionCategory category
    ) {
        validateSameDocument(question, category);

        if (category.isDeleted()) {
            throw new IllegalStateException(
                    "Deleted category cannot be used for question AI processing: "
                            + category.getId()
            );
        }
    }

    private void validateSameDocument(
            Question question,
            QuestionCategory category
    ) {
        if (!question.getDocument()
                .getId()
                .equals(category.getDocument().getId())) {
            throw new IllegalStateException(
                    "Question and category must belong to the same document."
            );
        }
    }

    private void validateResponse(
            QuestionAnalyzeResponse response
    ) {
        if (response == null) {
            throw new IllegalStateException(
                    "AI question response must not be null."
            );
        }

        if (response.relation() == null) {
            throw new IllegalStateException(
                    "AI response relation must not be null."
            );
        }

        if (response.relation() == QuestionScope.OTHER) {
            validateOtherResponse(response);
            return;
        }

        if (response.relation() == QuestionScope.COURSE_RELATED) {
            validateCourseRelatedResponse(response);
            return;
        }

        throw new IllegalStateException(
                "Unsupported AI question relation: "
                        + response.relation()
        );
    }

    private void validateOtherResponse(
            QuestionAnalyzeResponse response
    ) {
        if (response.primaryCategoryId() != null) {
            throw new IllegalStateException(
                    "OTHER question must not have primary category."
            );
        }

        if (response.embedding() != null) {
            throw new IllegalStateException(
                    "OTHER question must not have embedding."
            );
        }

        if (response.categories() != null
                && !response.categories().isEmpty()) {
            throw new IllegalStateException(
                    "OTHER question must not have categories."
            );
        }
    }

    private void validateCourseRelatedResponse(
            QuestionAnalyzeResponse response
    ) {
        if (response.primaryCategoryId() == null) {
            throw new IllegalStateException(
                    "COURSE_RELATED question must have primary category."
            );
        }

        if (response.embedding() == null
                || response.embedding().length != EMBEDDING_DIMENSION) {
            throw new IllegalStateException(
                    "COURSE_RELATED embedding dimension must be "
                            + EMBEDDING_DIMENSION
                            + "."
            );
        }

        if (response.categories() == null
                || response.categories().isEmpty()) {
            throw new IllegalStateException(
                    "COURSE_RELATED question must have categories."
            );
        }

        boolean primaryCategoryIncluded =
                response.categories()
                        .stream()
                        .anyMatch(category ->
                                category.categoryId()
                                        .equals(
                                                response.primaryCategoryId()
                                        )
                        );

        if (!primaryCategoryIncluded) {
            throw new IllegalStateException(
                    "Primary category must exist in categories."
            );
        }
    }

    private void markFailed(
            UUID questionId
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            Question question =
                    questionRepository.findById(questionId)
                            .orElse(null);

            if (question == null) {
                return;
            }

            // 실패 직전의 AI 결과 저장 Transaction은 rollback 되므로
            // 여기서 기존 Category Mapping을 삭제하면 안 된다.
            question.failAiProcessing();
        });
    }

    private boolean isBlank(
            String value
    ) {
        return value == null || value.isBlank();
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
            List<QuestionAnalyzeRequest.CategoryCandidate> categoryCandidates
    ) {
    }

    private record ResolvedContext(
            String slideContext,
            String documentContext
    ) {
    }
}
