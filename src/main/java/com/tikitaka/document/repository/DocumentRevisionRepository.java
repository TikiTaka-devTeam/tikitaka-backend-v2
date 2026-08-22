package com.tikitaka.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}