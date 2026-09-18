package com.tikitaka.question.entity;

import java.time.Instant;
import java.util.UUID;

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

    /*
     * AI 질문 분류 결과
     *
     * COURSE_RELATED:
     * 강의 내용과 관련된 학습 질문
     *
     * OTHER:
     * 시험 일정, 과제 일정 등 기타 질문
     *
     * AI 처리 전/실패 시 NULL 가능
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_scope", length = 20)
    private QuestionScope questionScope;

    /*
     * 여러 Category 중 Cluster 검색에 사용할 대표 Category
     *
     * OTHER 또는 AI 처리 전/실패 시 NULL
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_category_id")
    private QuestionCategory primaryCategory;

    /*
     * PENDING
     * PROCESSING
     * COMPLETED
     * FAILED
     */
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

    /*
     * AI 처리 시작
     */
    public void startAiProcessing() {
        this.aiProcessingStatus = AiProcessingStatus.PROCESSING;
    }

    /*
     * 강의 관련 질문 처리 완료
     */
    public void completeCourseRelatedProcessing(
            QuestionCategory primaryCategory
    ) {
        this.questionScope = QuestionScope.COURSE_RELATED;
        this.primaryCategory = primaryCategory;
        this.aiProcessingStatus = AiProcessingStatus.COMPLETED;
        this.aiProcessedAt = Instant.now();
    }

    /*
     * OTHER 질문 처리 완료
     *
     * OTHER에는 Category / Cluster 처리를 하지 않는다.
     */
    public void completeOtherProcessing() {
        this.questionScope = QuestionScope.OTHER;
        this.primaryCategory = null;
        this.aiProcessingStatus = AiProcessingStatus.COMPLETED;
        this.aiProcessedAt = Instant.now();
    }

    /*
     * AI 처리 실패
     *
     * 학생이 입력한 질문 원문은 그대로 유지한다.
     */
    public void failAiProcessing() {
        this.questionScope = null;
        this.primaryCategory = null;
        this.aiProcessingStatus = AiProcessingStatus.FAILED;
        this.aiProcessedAt = null;
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