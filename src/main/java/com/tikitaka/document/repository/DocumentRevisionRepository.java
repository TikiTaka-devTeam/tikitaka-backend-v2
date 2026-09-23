package com.tikitaka.document.repository;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.tikitaka.document.entity.DocumentRevision;
import com.tikitaka.document.entity.RevisionStatus;
import com.tikitaka.document.repository.projection.DocumentRevisionStatusProjection;

public interface DocumentRevisionRepository
        extends JpaRepository<DocumentRevision, UUID> {

    List<DocumentRevision> findAllByDocumentId(UUID documentId);

    List<DocumentRevision> findAllByStatus(RevisionStatus status);

    boolean existsByDocumentIdAndStatus(
            UUID documentId,
            RevisionStatus status
    );

    boolean existsByDocumentIdAndStatusIn(
            UUID documentId,
            List<RevisionStatus> statuses
    );

    Optional<DocumentRevision> findFirstByDocumentIdAndStatusIn(
            UUID documentId,
            List<RevisionStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select revision from DocumentRevision revision
            where revision.status = :status
              and revision.updatedAt < :updatedBefore
            """)
    List<DocumentRevision> findAllInactiveForUpdate(
            @Param("status") RevisionStatus status,
            @Param("updatedBefore") Instant updatedBefore
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select revision from DocumentRevision revision where revision.id = :revisionId")
    Optional<DocumentRevision> findByIdForUpdate(@Param("revisionId") UUID revisionId);

    @Query(
            value = """
                    SELECT DISTINCT ON (revision.document_id)
                        revision.id AS "revisionId",
                        revision.document_id AS "documentId",
                        revision.status AS status
                    FROM document_revisions revision
                    WHERE revision.document_id IN (:documentIds)
                      AND revision.status IN ('PROCESSING', 'COMPLETED', 'FAILED')
                    ORDER BY
                        revision.document_id,
                        revision.created_at DESC,
                        revision.id DESC
                    """,
            nativeQuery = true
    )
    List<DocumentRevisionStatusProjection> findLatestCompletionStatuses(
            @Param("documentIds") List<UUID> documentIds
    );
}
