package com.tikitaka.question.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
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
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseTimeEntity {

    private static final int EMBEDDING_DIMENSION = 768;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slide_id")
    private Slide slide;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "x_ratio")
    private Double xRatio;

    @Column(name = "y_ratio")
    private Double yRatio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.PENDING;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_scope", length = 20)
    private QuestionScope questionScope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_category_id")
    private QuestionCategory primaryCategory;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSION)
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private float[] embedding;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_processing_status", nullable = false, length = 20)
    private AiProcessingStatus aiProcessingStatus = AiProcessingStatus.PENDING;

    @Column(name = "ai_processed_at")
    private Instant aiProcessedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Question(
            Document document,
            Slide slide,
            User student,
            String title,
            String content,
            Double xRatio,
            Double yRatio
    ) {
        this.document = document;
        this.slide = slide;
        this.student = student;
        this.title = title;
        this.content = content;
        this.xRatio = xRatio;
        this.yRatio = yRatio;
        this.aiProcessingStatus = AiProcessingStatus.PENDING;
    }

    public static Question create(
            Document document,
            User student,
            String title,
            String content
    ) {
        return new Question(
                document,
                null,
                student,
                title,
                content,
                null,
                null
        );
    }

    public static Question createWithPin(
            Document document,
            Slide slide,
            User student,
            String title,
            String content,
            Double xRatio,
            Double yRatio
    ) {
        return new Question(
                document,
                slide,
                student,
                title,
                content,
                xRatio,
                yRatio
        );
    }

    public void startAiProcessing() {
        this.aiProcessingStatus = AiProcessingStatus.PROCESSING;
    }

    public void completeCourseRelatedProcessing(
            QuestionCategory primaryCategory,
            float[] embedding
    ) {
        validateEmbedding(embedding);

        this.questionScope = QuestionScope.COURSE_RELATED;
        this.primaryCategory = primaryCategory;
        this.embedding = embedding;
        this.aiProcessingStatus = AiProcessingStatus.COMPLETED;
        this.aiProcessedAt = Instant.now();
    }

    public void completeOtherProcessing() {
        this.questionScope = QuestionScope.OTHER;
        this.primaryCategory = null;
        this.embedding = null;
        this.aiProcessingStatus = AiProcessingStatus.COMPLETED;
        this.aiProcessedAt = Instant.now();
    }

    public void failAiProcessing() {
        this.questionScope = null;
        this.primaryCategory = null;
        this.embedding = null;
        this.aiProcessingStatus = AiProcessingStatus.FAILED;
        this.aiProcessedAt = null;
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "Embedding dimension must be " + EMBEDDING_DIMENSION + "."
            );
        }
    }

    public void markAnswered() {
        this.status = QuestionStatus.ANSWERED;
    }

    public void markPending() {
        this.status = QuestionStatus.PENDING;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}