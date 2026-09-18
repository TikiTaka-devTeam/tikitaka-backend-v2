package com.tikitaka.question.entity;

import java.util.UUID;

import com.tikitaka.document.entity.Document;
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
@Table(name = "question_clusters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionCluster extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private QuestionCategory category;

    @Column(name = "summary_title", nullable = false, length = 255)
    private String summaryTitle;

    /*
     * VECTOR(768)
     * pgvector/Hibernate 매핑 확정 후 추가
     */
    // private ... centroid;

    @Column(name = "member_count", nullable = false)
    private Integer memberCount = 1;

    private QuestionCluster(
            Document document,
            QuestionCategory category,
            String summaryTitle
    ) {
        this.document = document;
        this.category = category;
        this.summaryTitle = summaryTitle;
        this.memberCount = 1;
    }

    public static QuestionCluster create(
            Document document,
            QuestionCategory category,
            String summaryTitle
    ) {
        return new QuestionCluster(
                document,
                category,
                summaryTitle
        );
    }

    public void updateSummaryTitle(String summaryTitle) {
        this.summaryTitle = summaryTitle;
    }

    public void increaseMemberCount() {
        this.memberCount++;
    }
}