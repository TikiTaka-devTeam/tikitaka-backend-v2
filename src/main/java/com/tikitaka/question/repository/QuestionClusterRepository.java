package com.tikitaka.question.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.question.entity.QuestionCluster;
import com.tikitaka.question.repository.projection.ClusterSimilarityProjection;

import jakarta.persistence.LockModeType;

public interface QuestionClusterRepository
        extends JpaRepository<QuestionCluster, UUID> {

    List<QuestionCluster> findAllByDocumentIdOrderByCreatedAtAsc(
            UUID documentId
    );

    boolean existsByCategoryId(
            UUID categoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuestionCluster> findQuestionClusterById(
            UUID id
    );

    @Query(
            value = """
                    SELECT
                        qc.id AS clusterId,
                        1 - (
                            qc.centroid <=> CAST(:embedding AS vector)
                        ) AS similarity
                    FROM question_clusters qc
                    WHERE qc.document_id = :documentId
                      AND qc.category_id = :categoryId
                    ORDER BY
                        qc.centroid <=> CAST(:embedding AS vector)
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Optional<ClusterSimilarityProjection> findBestCluster(
            @Param("documentId") UUID documentId,
            @Param("categoryId") UUID categoryId,
            @Param("embedding") String embedding
    );
}
