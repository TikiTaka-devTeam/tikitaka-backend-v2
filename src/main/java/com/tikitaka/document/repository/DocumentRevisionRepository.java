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
}
