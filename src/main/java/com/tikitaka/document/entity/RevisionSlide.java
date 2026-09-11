package com.tikitaka.document.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "revision_slides")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionSlide {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private DocumentRevision revision;

    @Column(name = "source_page_number", nullable = false)
    private Integer sourcePageNumber;

    @Column(name = "thumbnail_key", nullable = false, columnDefinition = "TEXT")
    private String thumbnailKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RevisionSlide(
            DocumentRevision revision,
            Integer sourcePageNumber,
            String thumbnailKey
    ) {
        this.revision = revision;
        this.sourcePageNumber = sourcePageNumber;
        this.thumbnailKey = thumbnailKey;
    }

    public static RevisionSlide create(
            DocumentRevision revision,
            Integer sourcePageNumber,
            String thumbnailKey
    ) {
        return new RevisionSlide(revision, sourcePageNumber, thumbnailKey);
    }
}
