package com.tikitaka.question.repository.projection;

import java.util.UUID;

public interface QuestionSimilarityProjection {

    UUID getQuestionId();

    Double getSimilarity();
}