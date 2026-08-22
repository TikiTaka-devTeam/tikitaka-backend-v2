package com.tikitaka.question.entity;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.document.entity.Document;
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
@Table(name = "question_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private QuestionCategory(
            Document document,
            String name,
            User createdBy
    ) {
        this.document = document;
        this.name = name;
        this.createdBy = createdBy;
    }

    public static QuestionCategory createManual(
            Document document,
            String name,
            User createdBy
    ) {
        return new QuestionCategory(document, name, createdBy);
    }

    public static QuestionCategory createByAi(
            Document document,
            String name
    ) {
        return new QuestionCategory(document, name, null);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}