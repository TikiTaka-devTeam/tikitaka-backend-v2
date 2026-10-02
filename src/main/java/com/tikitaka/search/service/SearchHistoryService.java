package com.tikitaka.search.service;

import java.util.List;
import java.util.UUID;
import com.tikitaka.document.storage.DocumentStorage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.search.dto.response.RecentDocumentResponse;
import com.tikitaka.search.dto.response.RecentItemsResponse;
import com.tikitaka.search.dto.response.RecentQuestionResponse;
import com.tikitaka.search.dto.response.RecentSearchResponse;

@Service
public class SearchHistoryService {
    private final JdbcTemplate jdbcTemplate;
    private final DocumentStorage documentStorage;

    public SearchHistoryService(JdbcTemplate jdbcTemplate, DocumentStorage documentStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentStorage = documentStorage;
    }

    @Transactional(readOnly = true)
    public List<RecentSearchResponse> getRecentSearches(UUID userId) {
        return jdbcTemplate.query("""
                SELECT id, keyword, searched_at
                FROM recent_searches
                WHERE user_id = ?
                ORDER BY searched_at DESC, id DESC
                LIMIT 10
                """, (rs, rowNum) -> new RecentSearchResponse(
                        rs.getObject("id", UUID.class), rs.getString("keyword"),
                        rs.getTimestamp("searched_at").toInstant()), userId);
    }

    @Transactional
    public void deleteRecentSearch(UUID userId, UUID searchId) {
        jdbcTemplate.update(
                "DELETE FROM recent_searches WHERE id = ? AND user_id = ?", searchId, userId);
    }

    @Transactional
    public void deleteAllRecentSearches(UUID userId) {
        jdbcTemplate.update("DELETE FROM recent_searches WHERE user_id = ?", userId);
    }

    @Transactional(readOnly = true)
    public RecentItemsResponse getRecentItems(UUID userId) {
        List<RecentDocumentResponse> documents = jdbcTemplate.query("""
                SELECT d.id, d.space_id, s.space_name, d.title, rv.viewed_at, d.thumbnail_key
                FROM recent_document_views rv
                JOIN documents d ON d.id = rv.document_id
                JOIN spaces s ON s.id = d.space_id
                WHERE rv.user_id = ?
                  AND EXISTS (
                    SELECT 1 FROM space_members sm
                    WHERE sm.space_id = d.space_id AND sm.user_id = rv.user_id
                      AND sm.status = 'APPROVED' AND sm.removed_at IS NULL
                  )
                ORDER BY rv.viewed_at DESC, rv.id DESC
                LIMIT 3
                """, (rs, rowNum) -> new RecentDocumentResponse(
                        rs.getObject("id", UUID.class), rs.getObject("space_id", UUID.class),
                        rs.getString("space_name"), rs.getString("title"),
                        rs.getTimestamp("viewed_at").toInstant(),
                        documentStorage.presignedGetUrl(rs.getString("thumbnail_key"))), userId);

        List<RecentQuestionResponse> questions = jdbcTemplate.query("""
                SELECT q.id, d.space_id, s.space_name, q.title, rv.viewed_at, sl.thumbnail_key
                FROM recent_question_views rv
                JOIN questions q ON q.id = rv.question_id
                JOIN documents d ON d.id = q.document_id
                LEFT JOIN slides sl ON sl.id = q.slide_id
                JOIN spaces s ON s.id = d.space_id
                WHERE rv.user_id = ? AND q.is_deleted = FALSE
                  AND EXISTS (
                    SELECT 1 FROM space_members sm
                    WHERE sm.space_id = d.space_id AND sm.user_id = rv.user_id
                      AND sm.status = 'APPROVED' AND sm.removed_at IS NULL
                  )
                ORDER BY rv.viewed_at DESC, rv.id DESC
                LIMIT 3
                """, (rs, rowNum) -> new RecentQuestionResponse(
                        rs.getObject("id", UUID.class), rs.getObject("space_id", UUID.class),
                        rs.getString("space_name"), rs.getString("title"),
                        rs.getTimestamp("viewed_at").toInstant(),
                        thumbnailUrl(rs.getString("thumbnail_key"))), userId);
        return new RecentItemsResponse(documents, questions);
    }

    private String thumbnailUrl(String key) {
        return key == null ? null : documentStorage.presignedGetUrl(key);
    }

    @Transactional
    public void recordDocumentView(UUID userId, UUID documentId) {
        jdbcTemplate.update("""
                INSERT INTO recent_document_views (id, user_id, document_id, viewed_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (user_id, document_id)
                DO UPDATE SET viewed_at = EXCLUDED.viewed_at
                """, UUID.randomUUID(), userId, documentId);
    }

    @Transactional
    public void recordQuestionView(UUID userId, UUID questionId) {
        jdbcTemplate.update("""
                INSERT INTO recent_question_views (id, user_id, question_id, viewed_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (user_id, question_id)
                DO UPDATE SET viewed_at = EXCLUDED.viewed_at
                """, UUID.randomUUID(), userId, questionId);
    }
}
