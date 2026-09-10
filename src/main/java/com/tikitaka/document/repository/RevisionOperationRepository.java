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

    Optional<RevisionOperation> findFirstByRevisionIdOrderBySequenceDesc(UUID revisionId);

    Optional<RevisionOperation> findByRevisionIdAndSequence(UUID revisionId, Integer sequence);

    Optional<RevisionOperation> findFirstByRevisionIdAndStateAndSequenceLessThanOrderBySequenceDesc(
            UUID revisionId,
            RevisionOperationState state,
            Integer sequence
    );

    Optional<RevisionOperation> findFirstByRevisionIdAndStateAndSequenceGreaterThanOrderBySequenceAsc(
            UUID revisionId,
            RevisionOperationState state,
            Integer sequence
    );

    List<RevisionOperation> findAllByRevisionIdAndStateAndSequenceGreaterThanOrderBySequenceAsc(
            UUID revisionId,
            RevisionOperationState state,
            Integer sequence
    );

    boolean existsByRevisionIdAndClientOperationId(
            UUID revisionId,
            UUID clientOperationId
    );

    void deleteAllByRevisionId(UUID revisionId);
}
