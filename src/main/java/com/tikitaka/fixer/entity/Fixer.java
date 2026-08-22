package com.tikitaka.fixer.entity;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.document.entity.Slide;
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
@Table(name = "fixers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fixer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slide_id", nullable = false)
    private Slide slide;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private User professor;

    @Column(name = "x_ratio", nullable = false)
    private Double xRatio;

    @Column(name = "y_ratio", nullable = false)
    private Double yRatio;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_checked", nullable = false)
    private boolean checked = false;

    @Column(name = "checked_at")
    private Instant checkedAt;

    private Fixer(
            Slide slide,
            User professor,
            Double xRatio,
            Double yRatio,
            String content
    ) {
        this.slide = slide;
        this.professor = professor;
        this.xRatio = xRatio;
        this.yRatio = yRatio;
        this.content = content;
    }

    public static Fixer create(
            Slide slide,
            User professor,
            Double xRatio,
            Double yRatio,
            String content
    ) {
        return new Fixer(
                slide,
                professor,
                xRatio,
                yRatio,
                content
        );
    }

    public void check() {
        if (!this.checked) {
            this.checked = true;
            this.checkedAt = Instant.now();
        }
    }
}