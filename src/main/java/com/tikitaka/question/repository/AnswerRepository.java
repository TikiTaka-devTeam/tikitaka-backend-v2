package com.tikitaka.question.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.Answer;

public interface AnswerRepository
        extends JpaRepository<Answer, UUID> {

    List<Answer> findAllByQuestionIdAndDeletedFalseOrderByCreatedAtAsc(
            UUID questionId
    );

    long countByQuestionIdAndDeletedFalse(UUID questionId);
}