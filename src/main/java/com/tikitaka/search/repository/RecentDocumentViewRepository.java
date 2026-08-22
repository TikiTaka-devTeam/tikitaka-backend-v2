package com.tikitaka.search.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.search.entity.RecentDocumentView;

public interface RecentDocumentViewRepository
        extends JpaRepository<RecentDocumentView, UUID> {

    Optional<RecentDocumentView> findByUserIdAndDocumentId(
            UUID userId,
            UUID documentId
    );

    List<RecentDocumentView> findTop3ByUserIdOrderByViewedAtDesc(
            UUID userId
    );

    void deleteAllByDocumentId(UUID documentId);
}