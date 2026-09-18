package com.tikitaka.question.service;

import java.util.ArrayList;
import java.util.List;
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

    private final QuestionClusterService questionClusterService;

    private final TransactionTemplate transactionTemplate;

    /**
     * 질문 AI 처리 전체 흐름.
     *
     * AI HTTP 호출은 DB Transaction 밖에서 수행한다.
     */
    public void process(
            UUID questionId,
            String slideContext,
            String documentContext
    ) {
        Question question = markProcessing(questionId);

        try {
            QuestionAnalyzeResponse response =
                    analyze(
                            question,
                            slideContext,
                            documentContext
                    );

            transactionTemplate.executeWithoutResult(status ->
                    applyAnalysisResult(
                            questionId,
                            response
                    )
            );

        } catch (Exception exception) {

            markFailed(questionId);

            throw exception;
        }
    }

    /**
     * 질문을 PROCESSING 상태로 변경한다.
     */
    private Question markProcessing(UUID questionId) {

        return transactionTemplate.execute(status -> {

            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Question not found: " + questionId
                            )
                    );

            question.startAiProcessing();

            return questionRepository.save(question);
        });
    }

    /**
     * Python AI Server 호출.
     *
     * 여기서는 DB 변경을 수행하지 않는다.
     */
    public QuestionAnalyzeResponse analyze(
            Question question,
            String slideContext,
            String documentContext
    ) {
        List<QuestionCategory> categories =
                questionCategoryRepository
                        .findAllByDocumentIdAndDeletedFalse(
                                question.getDocument().getId()
                        );

        QuestionAnalyzeRequest request =
                new QuestionAnalyzeRequest(
                        question.getId(),
                        question.getTitle(),
                        question.getContent(),
                        slideContext,
                        documentContext,
                        categories.stream()
                                .map(category ->
                                        new QuestionAnalyzeRequest.CategoryCandidate(
                                                category.getId(),
                                                category.getName()
                                        )
                                )
                                .toList()
                );

        QuestionAnalyzeResponse response =
                questionAiClient.analyzeQuestion(request);

        validateResponse(response);

        return response;
    }

    /**
     * Python 분석 결과를 DB에 반영한다.
     *
     * 이 메서드는 TransactionTemplate 내부에서 호출된다.
     */
    private void applyAnalysisResult(
            UUID questionId,
            QuestionAnalyzeResponse response
    ) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Question not found: " + questionId
                        )
                );

        if (response.isOther()) {
            processOther(question);
            return;
        }

        processCourseRelated(
                question,
                response
        );
    }

    /**
     * OTHER 질문 처리.
     */
    private void processOther(
            Question question
    ) {
        question.completeOtherProcessing();

        questionRepository.save(question);
    }

    /**
     * COURSE_RELATED 질문 처리.
     */
    private void processCourseRelated(
            Question question,
            QuestionAnalyzeResponse response
    ) {
        QuestionCategory primaryCategory =
                getPrimaryCategory(
                        question,
                        response.primaryCategoryId()
                );

        List<QuestionCategory> selectedCategories =
                getSelectedCategories(
                        question,
                        response.categories()
                );

        saveCategoryMappings(
                question,
                selectedCategories
        );

        question.completeCourseRelatedProcessing(
                primaryCategory,
                response.embedding()
        );

        questionRepository.save(question);

        processCluster(
                question,
                primaryCategory,
                response.embedding()
        );
    }

    /**
     * AI가 선택한 Primary Category 조회.
     *
     * 반드시 현재 질문과 같은 Document에 속해야 한다.
     */
    private QuestionCategory getPrimaryCategory(
            Question question,
            UUID primaryCategoryId
    ) {
        QuestionCategory category =
                questionCategoryRepository
                        .findById(primaryCategoryId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Primary category not found: "
                                                + primaryCategoryId
                                )
                        );

        validateSameDocument(
                question,
                category
        );

        return category;
    }

    /**
     * AI가 반환한 Category 목록을 실제 Entity로 변환한다.
     */
    private List<QuestionCategory> getSelectedCategories(
            Question question,
            List<QuestionCategoryResult> categoryResults
    ) {
        List<QuestionCategory> categories =
                new ArrayList<>();

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

            validateSameDocument(
                    question,
                    category
            );

            categories.add(category);
        }

        return categories;
    }

    /**
     * question_category_mappings 저장.
     */
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

    /**
     * Cluster 처리.
     */
    private void processCluster(
            Question question,
            QuestionCategory primaryCategory,
            float[] embedding
    ) {
        questionClusterService
                .findMatchingCluster(
                        question.getDocument().getId(),
                        primaryCategory.getId(),
                        embedding
                )
                .ifPresentOrElse(

                        existingCluster ->
                                questionClusterService
                                        .assignToExistingCluster(
                                                existingCluster,
                                                question,
                                                embedding
                                        ),

                        () ->
                                createNewCluster(
                                        question,
                                        primaryCategory,
                                        embedding
                                )
                );
    }

    /**
     * 기존 Cluster가 없는 경우 신규 Cluster 생성.
     */
    private void createNewCluster(
            Question question,
            QuestionCategory primaryCategory,
            float[] embedding
    ) {
        ClusterTitleRequest request =
                new ClusterTitleRequest(
                        question.getTitle(),
                        question.getContent(),
                        primaryCategory.getName()
                );

        ClusterTitleResponse response =
                clusterAiClient.generateTitle(request);

        questionClusterService.createNewCluster(
                question.getDocument(),
                primaryCategory,
                question,
                response.summaryTitle(),
                embedding
        );
    }

    /**
     * 질문과 Category가 동일 Document에 속하는지 검증.
     */
    private void validateSameDocument(
            Question question,
            QuestionCategory category
    ) {
        if (!question.getDocument()
                .getId()
                .equals(
                        category.getDocument().getId()
                )) {

            throw new IllegalStateException(
                    "Question and category must belong to the same document."
            );
        }
    }

    private void validateResponse(
            QuestionAnalyzeResponse response
    ) {
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
                response.categories().stream()
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

    /**
     * AI 처리 실패 상태 저장.
     */
    private void markFailed(
            UUID questionId
    ) {
        transactionTemplate.executeWithoutResult(status -> {

            Question question =
                    questionRepository
                            .findById(questionId)
                            .orElse(null);

            if (question == null) {
                return;
            }

            question.failAiProcessing();

            questionRepository.save(question);
        });
    }
}