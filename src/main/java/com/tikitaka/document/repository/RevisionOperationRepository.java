package com.tikitaka.document.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.document.entity.RevisionOperation;
import com.tikitaka.document.entity.RevisionOperationState;

public interface RevisionOperationRepository
        extends JpaRepository<RevisionOperation, UUID> {

    List<RevisionOperation> findAllByRevisionIdOrderBySequenceAsc(
            UUID revisionId
    );

    Optional<RevisionOperation> findByRevisionIdAndClientOperationId(
            UUID revisionId,
            UUID clientOperationId
    );

    Optional<RevisionOperation>
    findFirstByRevisionIdAndStateOrderBySequenceDesc(
            UUID revisionId,
            RevisionOperationState state
    );

    boolean existsByRevisionIdAndClientOperationId(
            UUID revisionId,
            UUID clientOperationId
    );

    void deleteAllByRevisionId(UUID revisionId);
}