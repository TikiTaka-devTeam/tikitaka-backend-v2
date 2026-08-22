package com.tikitaka.question.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.QuestionCategory;

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
}