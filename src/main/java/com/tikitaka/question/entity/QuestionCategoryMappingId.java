package com.tikitaka.question.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionCategoryMappingId implements Serializable {

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "category_id")
    private UUID categoryId;

    public QuestionCategoryMappingId(
            UUID questionId,
            UUID categoryId
    ) {
        this.questionId = questionId;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof QuestionCategoryMappingId that)) {
            return false;
        }

        return Objects.equals(questionId, that.questionId)
                && Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, categoryId);
    }
}