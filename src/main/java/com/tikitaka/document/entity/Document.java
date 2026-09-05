package com.tikitaka.document.entity;

import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;
import com.tikitaka.space.entity.Space;

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

    @Column(name = "thumbnail_key", nullable = false, columnDefinition = "TEXT")
    private String thumbnailKey;

    @Column(name = "pdf_key", nullable = false, columnDefinition = "TEXT")
    private String pdfKey;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    @Column(nullable = false)
    private Integer version = 1;

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
    }
}