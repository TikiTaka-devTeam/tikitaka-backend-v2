package com.tikitaka.question.entity;

import java.util.UUID;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    private static final int EMBEDDING_DIMENSION = 768;

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

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSION)
    @Column(name = "centroid", nullable = false, columnDefinition = "vector(768)")
    private float[] centroid;

    @Column(name = "member_count", nullable = false)
    private Integer memberCount = 1;

    private QuestionCluster(
            Document document,
            QuestionCategory category,
            String summaryTitle,
            float[] centroid
    ) {
        validateEmbedding(centroid);

        this.document = document;
        this.category = category;
        this.summaryTitle = summaryTitle;
        this.centroid = centroid;
        this.memberCount = 1;
    }

    public static QuestionCluster create(
            Document document,
            QuestionCategory category,
            String summaryTitle,
            float[] centroid
    ) {
        return new QuestionCluster(
                document,
                category,
                summaryTitle,
                centroid
        );
    }

    public void updateSummaryTitle(String summaryTitle) {
        this.summaryTitle = summaryTitle;
    }

    public void addMember(float[] embedding) {
        validateEmbedding(embedding);

        int previousCount = this.memberCount;
        int newCount = previousCount + 1;

        float[] newCentroid = new float[EMBEDDING_DIMENSION];

        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            newCentroid[i] =
                    (this.centroid[i] * previousCount + embedding[i])
                            / newCount;
        }

        this.centroid = newCentroid;
        this.memberCount = newCount;
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "Embedding dimension must be " + EMBEDDING_DIMENSION + "."
            );
        }
    }
}