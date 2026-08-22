package com.tikitaka.assignment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;
import com.tikitaka.space.entity.Space;
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
@Table(name = "assignments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "auto_close", nullable = false)
    private boolean autoClose = true;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "max_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxScore = BigDecimal.valueOf(100);

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false, length = 20)
    private GradingStatus gradingStatus = GradingStatus.DRAFT;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Assignment(
            Space space,
            User author,
            String title,
            String description,
            Instant dueAt,
            boolean autoClose
    ) {
        this.space = space;
        this.author = author;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.autoClose = autoClose;
    }

    public static Assignment create(
            Space space,
            User author,
            String title,
            String description,
            Instant dueAt,
            boolean autoClose
    ) {
        return new Assignment(
                space,
                author,
                title,
                description,
                dueAt,
                autoClose
        );
    }

    public void update(
            String title,
            String description,
            Instant dueAt,
            boolean autoClose
    ) {
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.autoClose = autoClose;
    }

    public void close() {
        this.closedAt = Instant.now();
    }

    public void updateMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public void finalizeGrades() {
        this.gradingStatus = GradingStatus.FINALIZED;
        this.finalizedAt = Instant.now();
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }

    public boolean isClosed() {
        return this.closedAt != null;
    }
}