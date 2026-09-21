package com.tikitaka.search.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query(
            value = """
                    INSERT INTO recent_question_views (user_id, question_id)
                    VALUES (:userId, :questionId)
                    ON CONFLICT (user_id, question_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("questionId") UUID questionId
    );

    @Modifying
    @Query(
            value = """
                    UPDATE recent_question_views
                    SET viewed_at = NOW()
                    WHERE user_id = :userId AND question_id = :questionId
                    """,
            nativeQuery = true
    )
    void refreshViewedAt(
            @Param("userId") UUID userId,
            @Param("questionId") UUID questionId
    );

    void deleteAllByQuestionId(UUID questionId);
}
