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
public class QuestionLikeId implements Serializable {

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "user_id")
    private UUID userId;

    public QuestionLikeId(
            UUID questionId,
            UUID userId
    ) {
        this.questionId = questionId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof QuestionLikeId that)) {
            return false;
        }

        return Objects.equals(questionId, that.questionId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, userId);
    }
}