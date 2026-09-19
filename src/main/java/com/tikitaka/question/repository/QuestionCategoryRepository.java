package com.tikitaka.question.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.tikitaka.question.entity.QuestionCategory;

import jakarta.persistence.LockModeType;

public interface QuestionCategoryRepository
        extends JpaRepository<QuestionCategory, UUID> {

    List<QuestionCategory> findAllByDocumentIdAndDeletedFalse(
            UUID documentId
    );

    Optional<QuestionCategory> findByDocumentIdAndNameAndDeletedFalse(
            UUID documentId,
            String name
    );

    boolean existsByDocumentIdAndNameAndDeletedFalse(
            UUID documentId,
            String name
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuestionCategory> findQuestionCategoryById(
            UUID id
    );
}
