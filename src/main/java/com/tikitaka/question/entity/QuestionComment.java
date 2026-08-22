package com.tikitaka.question.entity;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;
import com.tikitaka.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "question_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private QuestionComment parentComment;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private QuestionComment(
            Question question,
            User author,
            QuestionComment parentComment,
            String content
    ) {
        this.question = question;
        this.author = author;
        this.parentComment = parentComment;
        this.content = content;
    }

    public static QuestionComment create(
            Question question,
            User author,
            QuestionComment parentComment,
            String content
    ) {
        return new QuestionComment(
                question,
                author,
                parentComment,
                content
        );
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}