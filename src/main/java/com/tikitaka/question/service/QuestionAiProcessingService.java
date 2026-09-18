package com.tikitaka.question.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tikitaka.question.ai.QuestionAiClient;
import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.ai.dto.QuestionAnalyzeResponse;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionScope;
import com.tikitaka.question.repository.QuestionCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionAiProcessingService {

    private static final int EMBEDDING_DIMENSION = 768;

    private final QuestionAiClient questionAiClient;
    private final QuestionCategoryRepository questionCategoryRepository;

    /**
     * 질문을 Python AI Server에 전달하고 분석 결과를 반환한다.
     *
     * 여기서는 DB 변경을 수행하지 않는다.
     * AI HTTP 호출과 DB Transaction을 길게 묶지 않기 위함이다.
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
}