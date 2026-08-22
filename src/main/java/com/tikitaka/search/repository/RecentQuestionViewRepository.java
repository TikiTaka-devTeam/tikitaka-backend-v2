package com.tikitaka.search.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.search.entity.RecentQuestionView;

public interface RecentQuestionViewRepository
        extends JpaRepository<RecentQuestionView, UUID> {

    Optional<RecentQuestionView> findByUserIdAndQuestionId(
            UUID userId,
            UUID questionId
    );

    List<RecentQuestionView> findTop3ByUserIdOrderByViewedAtDesc(
            UUID userId
    );

    void deleteAllByQuestionId(UUID questionId);
}