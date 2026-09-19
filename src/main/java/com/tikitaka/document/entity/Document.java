package com.tikitaka.document.entity;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;
import com.tikitaka.space.entity.Space;

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
@Table(name = "documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(
            name = "thumbnail_key",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String thumbnailKey;

    @Column(
            name = "pdf_key",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String pdfKey;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "category_processing_status",
            nullable = false,
            length = 20
    )
    private CategoryProcessingStatus categoryProcessingStatus =
            CategoryProcessingStatus.PENDING;

    @Column(name = "category_processed_at")
    private Instant categoryProcessedAt;

    private Document(
            Space space,
            String title,
            String thumbnailKey,
            String pdfKey,
            Integer pageCount
    ) {
        this.space = space;
        this.title = title;
        this.thumbnailKey = thumbnailKey;
        this.pdfKey = pdfKey;
        this.pageCount = pageCount;
        this.version = 1;
        this.categoryProcessingStatus =
                CategoryProcessingStatus.PENDING;
        this.categoryProcessedAt = null;
    }

    public static Document create(
            Space space,
            String title,
            String thumbnailKey,
            String pdfKey,
            Integer pageCount
    ) {
        return new Document(
                space,
                title,
                thumbnailKey,
                pdfKey,
                pageCount
        );
    }

    public void replace(
            String thumbnailKey,
            String pdfKey,
            Integer pageCount
    ) {
        this.thumbnailKey = thumbnailKey;
        this.pdfKey = pdfKey;
        this.pageCount = pageCount;
        this.version++;

        markCategoryProcessingPending();
    }

    public void markCategoryProcessingPending() {
        this.categoryProcessingStatus =
                CategoryProcessingStatus.PENDING;

        this.categoryProcessedAt = null;
    }

    public void startCategoryProcessing() {
        this.categoryProcessingStatus =
                CategoryProcessingStatus.PROCESSING;

        this.categoryProcessedAt = null;
    }

    public void completeCategoryProcessing() {
        this.categoryProcessingStatus =
                CategoryProcessingStatus.COMPLETED;

        this.categoryProcessedAt = Instant.now();
    }

    public void failCategoryProcessing() {
        this.categoryProcessingStatus =
                CategoryProcessingStatus.FAILED;

        this.categoryProcessedAt = null;
    }
}