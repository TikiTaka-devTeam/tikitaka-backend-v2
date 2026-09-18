package com.tikitaka.question.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.question.ai.QuestionAiClient;
import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.ai.dto.QuestionAnalyzeResponse;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.question.repository.projection.QuestionSimilarityProjection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimilarQuestionService {

    private static final int EMBEDDING_DIMENSION = 768;

    private final QuestionAiClient questionAiClient;
    private final QuestionRepository questionRepository;
    private final QuestionCategoryRepository questionCategoryRepository;

    public List<Result> findSimilarQuestions(
            UUID documentId,
            String title,
            String content
    ) {
        List<QuestionCategory> categories =
                questionCategoryRepository
                        .findAllByDocumentIdAndDeletedFalse(
                                documentId
                        );

        QuestionAnalyzeRequest request =
                new QuestionAnalyzeRequest(
                        UUID.randomUUID(),
                        title,
                        content,
                        null,
                        null,
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
                questionAiClient.analyzeQuestion(
                        request
                );

        if (response.isOther()) {
            return List.of();
        }

        validateEmbedding(
                response.embedding()
        );

        String pgVector =
                toPgVector(
                        response.embedding()
                );

        List<QuestionSimilarityProjection> similarities =
                questionRepository.findSimilarQuestions(
                        documentId,
                        pgVector
                );

        return similarities.stream()
                .map(this::toResult)
                .toList();
    }

    private Result toResult(
            QuestionSimilarityProjection projection
    ) {
        Question question =
                questionRepository
                        .findById(
                                projection.getQuestionId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Similar question not found: "
                                                + projection.getQuestionId()
                                )
                        );

        return new Result(
                question,
                projection.getSimilarity()
        );
    }

    private void validateEmbedding(
            float[] embedding
    ) {
        if (embedding == null
                || embedding.length != EMBEDDING_DIMENSION) {

            throw new IllegalStateException(
                    "Similar question embedding dimension must be "
                            + EMBEDDING_DIMENSION
                            + "."
            );
        }
    }

    private String toPgVector(
            float[] embedding
    ) {
        StringBuilder builder =
                new StringBuilder("[");

        for (int index = 0;
             index < embedding.length;
             index++) {

            if (index > 0) {
                builder.append(',');
            }

            builder.append(
                    embedding[index]
            );
        }

        builder.append(']');

        return builder.toString();
    }

    public record Result(
            Question question,
            double similarity
    ) {
    }
}