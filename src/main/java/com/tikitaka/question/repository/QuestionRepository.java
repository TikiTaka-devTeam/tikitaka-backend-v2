package com.tikitaka.question.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionStatus;

public interface QuestionRepository
        extends JpaRepository<Question, UUID> {

    List<Question> findAllByDocumentIdAndDeletedFalse(UUID documentId);

    List<Question> findAllByStudentIdAndDeletedFalse(UUID studentId);

    List<Question> findAllByDocumentIdAndStatusAndDeletedFalse(
            UUID documentId,
            QuestionStatus status
    );

    List<Question> findAllBySlideIdAndDeletedFalse(UUID slideId);

    long countByStudentIdAndStatusAndDeletedFalse(
            UUID studentId,
            QuestionStatus status
    );
}