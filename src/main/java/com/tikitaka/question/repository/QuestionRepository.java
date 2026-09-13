package com.tikitaka.question.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionStatus;

public interface QuestionRepository
        extends JpaRepository<Question, UUID> {

    List<Question> findAllByDocumentIdAndDeletedFalse(UUID documentId);

    List<Question> findAllByDocumentSpaceIdAndDeletedFalse(UUID spaceId);

    List<Question> findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(UUID spaceId, UUID studentId);

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

    long countByDocumentSpaceIdAndStudentIdAndDeletedFalse(UUID spaceId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Question> findQuestionById(UUID id);
}
