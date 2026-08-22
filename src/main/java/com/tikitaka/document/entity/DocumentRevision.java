package com.tikitaka.document.entity;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "document_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentRevision extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "editor_id", nullable = false)
    private User editor;

    @Column(name = "base_document_version", nullable = false)
    private Integer baseDocumentVersion;

    @Column(name = "preview_version", nullable = false)
    private Integer previewVersion = 1;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_pdf_url", columnDefinition = "TEXT")
    private String sourcePdfUrl;

    @Column(name = "source_page_count")
    private Integer sourcePageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisionStatus status = RevisionStatus.EDITING;

    @Column(name = "completed_at")
    private Instant completedAt;

    private DocumentRevision(
            Document document,
            User editor
    ) {
        this.document = document;
        this.editor = editor;
        this.baseDocumentVersion = document.getVersion();
        this.previewVersion = 1;
        this.status = RevisionStatus.EDITING;
    }

    public static DocumentRevision create(
            Document document,
            User editor
    ) {
        return new DocumentRevision(document, editor);
    }

    public void updateSourcePdf(
            String sourceFileName,
            String sourcePdfUrl,
            Integer sourcePageCount
    ) {
        this.sourceFileName = sourceFileName;
        this.sourcePdfUrl = sourcePdfUrl;
        this.sourcePageCount = sourcePageCount;
    }

    public void increasePreviewVersion() {
        this.previewVersion++;
    }

    public void startProcessing() {
        this.status = RevisionStatus.PROCESSING;
    }

    public void complete() {
        this.status = RevisionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        this.status = RevisionStatus.CANCELED;
    }
}