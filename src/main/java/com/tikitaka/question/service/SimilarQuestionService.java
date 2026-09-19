package com.tikitaka.question.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionScope;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.question.repository.projection.QuestionSimilarityProjection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimilarQuestionService {

    private static final int
            EMBEDDING_DIMENSION =
            768;

    private final QuestionRepository
            questionRepository;

    private final QuestionCategoryMappingRepository
            questionCategoryMappingRepository;

    public List<Result> findSimilarQuestions(
            UUID questionId
    ) {

        Question source =
                questionRepository
                        .findById(
                                questionId
                        )
                        .filter(
                                question ->
                                        !question
                                                .isDeleted()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Question not found: "
                                                        + questionId
                                        )
                        );

        if (source.getQuestionScope()
                != QuestionScope.COURSE_RELATED) {

            return List.of();
        }

        validateEmbedding(
                source.getEmbedding()
        );

        List<UUID> categoryIds =
                questionCategoryMappingRepository
                        .findAllByQuestionId(
                                questionId
                        )
                        .stream()
                        .map(
                                mapping ->
                                        mapping
                                                .getCategory()
                                                .getId()
                        )
                        .distinct()
                        .toList();

        if (categoryIds.isEmpty()) {
            return List.of();
        }

        String pgVector =
                toPgVector(
                        source.getEmbedding()
                );

        return questionRepository
                .findSimilarQuestionsInCategories(
                        source
                                .getDocument()
                                .getId(),
                        source.getId(),
                        categoryIds,
                        pgVector
                )
                .stream()
                .map(
                        this::toResult
                )
                .toList();
    }

    private Result toResult(
            QuestionSimilarityProjection
                    projection
    ) {

        Question question =
                questionRepository
                        .findById(
                                projection
                                        .getQuestionId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Similar question not found: "
                                                        + projection
                                                        .getQuestionId()
                                        )
                        );

        Double similarity =
                projection
                        .getSimilarity();

        return new Result(
                question,
                similarity == null
                        ? 0.0
                        : similarity
        );
    }

    private void validateEmbedding(
            float[] embedding
    ) {

        if (embedding == null
                || embedding.length
                != EMBEDDING_DIMENSION) {

            throw new IllegalStateException(
                    "Question embedding dimension must be "
                            + EMBEDDING_DIMENSION
                            + "."
            );
        }
    }

    private String toPgVector(
            float[] embedding
    ) {

        StringBuilder builder =
                new StringBuilder(
                        "["
                );

        for (
                int index = 0;
                index < embedding.length;
                index++
        ) {

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