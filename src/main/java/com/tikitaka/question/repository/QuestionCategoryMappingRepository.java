package com.tikitaka.question.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.entity.QuestionCategoryMappingId;

public interface QuestionCategoryMappingRepository
        extends JpaRepository<
        QuestionCategoryMapping,
        QuestionCategoryMappingId
        > {

    List<QuestionCategoryMapping>
    findAllByQuestionId(
            UUID questionId
    );

    List<QuestionCategoryMapping>
    findAllByCategoryId(
            UUID categoryId
    );

    boolean existsByCategoryId(
            UUID categoryId
    );

    void deleteAllByQuestionId(
            UUID questionId
    );
}