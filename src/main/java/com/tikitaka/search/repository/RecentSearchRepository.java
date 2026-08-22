package com.tikitaka.search.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.search.entity.RecentSearch;

public interface RecentSearchRepository
        extends JpaRepository<RecentSearch, UUID> {

    Optional<RecentSearch> findByUserIdAndKeyword(
            UUID userId,
            String keyword
    );

    List<RecentSearch> findTop10ByUserIdOrderBySearchedAtDesc(
            UUID userId
    );

    void deleteByIdAndUserId(
            UUID id,
            UUID userId
    );

    void deleteAllByUserId(UUID userId);

    long countByUserId(UUID userId);
}