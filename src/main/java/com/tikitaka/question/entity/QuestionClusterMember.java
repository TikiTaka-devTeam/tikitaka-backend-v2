package com.tikitaka.question.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "question_cluster_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionClusterMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cluster_id", nullable = false)
    private QuestionCluster cluster;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private Float similarity;

    @Column(name = "is_representative", nullable = false)
    private boolean representative = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private QuestionClusterMember(
            QuestionCluster cluster,
            Question question,
            Float similarity,
            boolean representative
    ) {
        this.cluster = cluster;
        this.question = question;
        this.similarity = similarity;
        this.representative = representative;
    }

    public static QuestionClusterMember createRepresentative(
            QuestionCluster cluster,
            Question question
    ) {
        return new QuestionClusterMember(
                cluster,
                question,
                1.0f,
                true
        );
    }

    public static QuestionClusterMember createMember(
            QuestionCluster cluster,
            Question question,
            Float similarity
    ) {
        return new QuestionClusterMember(
                cluster,
                question,
                similarity,
                false
        );
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}