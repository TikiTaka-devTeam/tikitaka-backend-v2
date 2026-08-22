package com.tikitaka.document.entity;

import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;

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
@Table(name = "slides")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slide extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "thumbnail_url", nullable = false, columnDefinition = "TEXT")
    private String thumbnailUrl;

    private Slide(
            Document document,
            Integer pageNumber,
            String thumbnailUrl
    ) {
        this.document = document;
        this.pageNumber = pageNumber;
        this.thumbnailUrl = thumbnailUrl;
    }

    public static Slide create(
            Document document,
            Integer pageNumber,
            String thumbnailUrl
    ) {
        return new Slide(document, pageNumber, thumbnailUrl);
    }
}