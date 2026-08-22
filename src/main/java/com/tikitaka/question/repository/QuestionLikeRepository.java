package com.tikitaka.question.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.QuestionLike;
import com.tikitaka.question.entity.QuestionLikeId;

public interface QuestionLikeRepository
        extends JpaRepository<QuestionLike, QuestionLikeId> {

    boolean existsByQuestionIdAndUserId(
            UUID questionId,
            UUID userId
    );

    void deleteByQuestionIdAndUserId(
            UUID questionId,
            UUID userId
    );

    long countByQuestionId(UUID questionId);
}