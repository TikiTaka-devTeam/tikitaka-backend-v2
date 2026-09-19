package com.tikitaka.question.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tikitaka.document.entity.Document;
import com.tikitaka.global.common.entity.BaseTimeEntity;
import com.tikitaka.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(
            name = "source_type",
            nullable = false,
            length = 20
    )
    private CategorySourceType sourceType =
            CategorySourceType.MANUAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "source_pages",
            columnDefinition = "jsonb"
    )
    private List<Integer> sourcePages;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private QuestionCategory(
            Document document,
            String name,
            User createdBy,
            CategorySourceType sourceType,
            List<Integer> sourcePages
    ) {
        this.document = document;
        this.name = name;
        this.createdBy = createdBy;
        this.sourceType = sourceType;

        this.sourcePages =
                sourcePages == null
                        ? null
                        : List.copyOf(sourcePages);
    }

    public static QuestionCategory createManual(
            Document document,
            String name,
            User createdBy
    ) {
        return new QuestionCategory(
                document,
                name,
                createdBy,
                CategorySourceType.MANUAL,
                null
        );
    }

    public static QuestionCategory createByAi(
            Document document,
            String name,
            List<Integer> sourcePages
    ) {
        return new QuestionCategory(
                document,
                name,
                null,
                CategorySourceType.AI,
                sourcePages
        );
    }

    public void updateName(
            String name
    ) {
        this.name = name;
    }

    public void updateAiSourcePages(
            List<Integer> sourcePages
    ) {
        if (sourceType != CategorySourceType.AI) {
            return;
        }

        this.sourcePages =
                sourcePages == null
                        ? null
                        : List.copyOf(sourcePages);
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}