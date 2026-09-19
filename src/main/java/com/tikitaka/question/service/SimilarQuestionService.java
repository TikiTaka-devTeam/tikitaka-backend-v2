package com.tikitaka.question.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.question.ai.QuestionAiClient;
import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.ai.dto.QuestionAnalyzeResponse;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.question.repository.projection.QuestionSimilarityProjection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SimilarQuestionService {

    private static final int EMBEDDING_DIMENSION = 768;

    private final QuestionAiClient questionAiClient;
    private final QuestionRepository questionRepository;
    private final QuestionCategoryRepository questionCategoryRepository;
    private final QuestionAiContextService questionAiContextService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<Result> findSimilarQuestions(
            UUID documentId,
            UUID slideId,
            String title,
            String content
    ) {
        List<QuestionAnalyzeRequest.CategoryCandidate> categoryCandidates =
                transactionTemplate.execute(status ->
                        questionCategoryRepository
                                .findAllByDocumentIdAndDeletedFalse(documentId)
                                .stream()
                                .map(category ->
                                        new QuestionAnalyzeRequest.CategoryCandidate(
                                                category.getId(),
                                                category.getName()
                                        )
                                )
                                .toList()
                );

        if (categoryCandidates == null) {
            categoryCandidates = List.of();
        }

        QuestionAiContextService.Context context =
                questionAiContextService.load(
                        documentId,
                        slideId
                );

        QuestionAnalyzeRequest request =
                new QuestionAnalyzeRequest(
                        UUID.randomUUID(),
                        title,
                        content,
                        context.slideContext(),
                        context.documentContext(),
                        categoryCandidates
                );

        QuestionAnalyzeResponse response =
                questionAiClient.analyzeQuestion(request);

        if (response.isOther()) {
            return List.of();
        }

        validateEmbedding(response.embedding());

        String pgVector =
                toPgVector(response.embedding());

        List<Result> results =
                transactionTemplate.execute(status ->
                        questionRepository
                                .findSimilarQuestions(
                                        documentId,
                                        pgVector
                                )
                                .stream()
                                .map(this::toResult)
                                .toList()
                );

        return results == null
                ? List.of()
                : results;
    }

    private Result toResult(
            QuestionSimilarityProjection projection
    ) {
        Question question =
                questionRepository
                        .findById(projection.getQuestionId())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Similar question not found: "
                                                + projection.getQuestionId()
                                )
                        );

        Double similarity =
                projection.getSimilarity();

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

            builder.append(embedding[index]);
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
