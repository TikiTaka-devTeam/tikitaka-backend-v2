package com.tikitaka.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.document.entity.RevisionPage;

public interface RevisionPageRepository
        extends JpaRepository<RevisionPage, UUID> {

    List<RevisionPage> findAllByRevisionIdOrderByPositionAsc(
            UUID revisionId
    );

    void deleteAllByRevisionId(UUID revisionId);
}