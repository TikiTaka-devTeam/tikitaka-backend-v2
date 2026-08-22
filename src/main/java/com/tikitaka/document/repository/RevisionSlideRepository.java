package com.tikitaka.document.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.document.entity.RevisionSlide;

public interface RevisionSlideRepository
        extends JpaRepository<RevisionSlide, UUID> {

    List<RevisionSlide> findAllByRevisionIdOrderBySourcePageNumberAsc(
            UUID revisionId
    );

    Optional<RevisionSlide> findByRevisionIdAndSourcePageNumber(
            UUID revisionId,
            Integer sourcePageNumber
    );

    void deleteAllByRevisionId(UUID revisionId);
}