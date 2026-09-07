package com.tikitaka.document.entity;

import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;

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
@Table(name = "revision_pages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionPage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private DocumentRevision revision;

    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private RevisionSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_slide_id")
    private Slide originalSlide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_slide_id")
    private RevisionSlide revisionSlide;

    @Column(name = "thumbnail_key", nullable = false, columnDefinition = "TEXT")
    private String thumbnailKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisionPageStatus status = RevisionPageStatus.ACTIVE;

    private RevisionPage(
            DocumentRevision revision,
            Integer position,
            RevisionSourceType sourceType,
            Slide originalSlide,
            RevisionSlide revisionSlide,
            String thumbnailKey
    ) {
        this.revision = revision;
        this.position = position;
        this.sourceType = sourceType;
        this.originalSlide = originalSlide;
        this.revisionSlide = revisionSlide;
        this.thumbnailKey = thumbnailKey;
        this.status = RevisionPageStatus.ACTIVE;
    }

    public static RevisionPage original(
            DocumentRevision revision,
            Integer position,
            Slide originalSlide
    ) {
        return new RevisionPage(
                revision,
                position,
                RevisionSourceType.ORIGINAL,
                originalSlide,
                null,
                originalSlide.getThumbnailKey());
    }

    public static RevisionPage revision(
            DocumentRevision revision,
            Integer position,
            RevisionSlide revisionSlide
    ) {
        return new RevisionPage(
                revision,
                position,
                RevisionSourceType.REVISION,
                null,
                revisionSlide,
                revisionSlide.getThumbnailKey());
    }

    public void changePosition(Integer position) {
        this.position = position;
    }

    public void markDeletePending() {
        this.status = RevisionPageStatus.DELETE_PENDING;
    }

    public void reactivate() {
        this.status = RevisionPageStatus.ACTIVE;
    }
}
