package com.tikitaka.search.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.search.dto.SearchAnnouncementResponse;
import com.tikitaka.search.dto.SearchCategoryResponse;
import com.tikitaka.search.dto.SearchDocumentResponse;
import com.tikitaka.search.dto.SearchQuestionResponse;
import com.tikitaka.search.dto.SearchResponse;

@Service
public class SearchService {
    private static final int CONTENT_PREVIEW_LENGTH = 100;

    private final JdbcTemplate jdbcTemplate;

    public SearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SearchResponse search(UUID userId, String rawKeyword) {
        String keyword = normalizeKeyword(rawKeyword);
        saveRecentSearch(userId, keyword);

        List<SearchDocumentResponse> documents = jdbcTemplate.query("""
                SELECT d.id, d.space_id, s.space_name, d.title, d.thumbnail_url, d.created_at
                FROM documents d
                JOIN spaces s ON s.id = d.space_id
                WHERE d.title ILIKE ? ESCAPE '\\'
                  AND EXISTS (
                    SELECT 1 FROM space_members sm
                    WHERE sm.space_id = d.space_id
                      AND sm.user_id = ?
                      AND sm.status = 'APPROVED'
                      AND sm.removed_at IS NULL
                  )
                ORDER BY d.created_at DESC, d.id DESC
                """, this::mapDocument, containsPattern(keyword), userId);

        List<SearchAnnouncementResponse> announcements = jdbcTemplate.query("""
                SELECT n.id, n.space_id, s.space_name, n.title, n.content, n.created_at
                FROM space_notices n
                JOIN spaces s ON s.id = n.space_id
                WHERE (n.title ILIKE ? ESCAPE '\\' OR n.content ILIKE ? ESCAPE '\\')
                  AND EXISTS (
                    SELECT 1 FROM space_members sm
                    WHERE sm.space_id = n.space_id
                      AND sm.user_id = ?
                      AND sm.status = 'APPROVED'
                      AND sm.removed_at IS NULL
                  )
                ORDER BY n.created_at DESC, n.id DESC
                """, this::mapAnnouncement, containsPattern(keyword), containsPattern(keyword), userId);

        List<QuestionRow> questionRows = jdbcTemplate.query("""
                SELECT q.id, d.space_id, s.space_name, q.title, q.content, q.created_at
                FROM questions q
                JOIN documents d ON d.id = q.document_id
                JOIN spaces s ON s.id = d.space_id
                WHERE (q.title ILIKE ? ESCAPE '\\' OR q.content ILIKE ? ESCAPE '\\')
                  AND q.is_deleted = FALSE
                  AND EXISTS (
                    SELECT 1 FROM space_members sm
                    WHERE sm.space_id = d.space_id
                      AND sm.user_id = ?
                      AND sm.status = 'APPROVED'
                      AND sm.removed_at IS NULL
                  )
                ORDER BY q.created_at DESC, q.id DESC
                """, this::mapQuestionRow, containsPattern(keyword), containsPattern(keyword), userId);

        Map<UUID, List<SearchCategoryResponse>> categories = findCategories(questionRows);
        List<SearchQuestionResponse> questions = questionRows.stream()
                .map(row -> new SearchQuestionResponse(
                        row.questionId(), row.spaceId(), row.spaceName(), row.title(),
                        preview(row.content()), categories.getOrDefault(row.questionId(), List.of()),
                        row.createdAt()))
                .toList();

        return new SearchResponse(documents, announcements, questions);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어는 공백일 수 없습니다.");
        }
        return keyword.trim();
    }

    private String containsPattern(String keyword) {
        return "%" + keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private void saveRecentSearch(UUID userId, String keyword) {
        jdbcTemplate.update("""
                INSERT INTO recent_searches (id, user_id, keyword, searched_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (user_id, keyword)
                DO UPDATE SET searched_at = EXCLUDED.searched_at
                """, UUID.randomUUID(), userId, keyword);
        jdbcTemplate.update("""
                DELETE FROM recent_searches
                WHERE id IN (
                  SELECT id FROM recent_searches
                  WHERE user_id = ?
                  ORDER BY searched_at DESC, id DESC
                  OFFSET 10
                )
                """, userId);
    }

    private Map<UUID, List<SearchCategoryResponse>> findCategories(List<QuestionRow> questions) {
        Map<UUID, List<SearchCategoryResponse>> result = new LinkedHashMap<>();
        for (QuestionRow question : questions) {
            result.put(question.questionId(), new ArrayList<>());
        }
        if (questions.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(questions.size(), "?"));
        Object[] ids = questions.stream().map(QuestionRow::questionId).toArray();
        jdbcTemplate.query("""
                SELECT m.question_id, c.id, c.name
                FROM question_category_mappings m
                JOIN question_categories c ON c.id = m.category_id
                WHERE m.question_id IN (%s)
                  AND c.is_deleted = FALSE
                ORDER BY c.name ASC, c.id ASC
                """.formatted(placeholders), (RowCallbackHandler) rs -> result.get(rs.getObject("question_id", UUID.class))
                        .add(new SearchCategoryResponse(
                                rs.getObject("id", UUID.class), rs.getString("name"))), ids);
        return result;
    }

    private SearchDocumentResponse mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new SearchDocumentResponse(
                rs.getObject("id", UUID.class), rs.getObject("space_id", UUID.class),
                rs.getString("space_name"), rs.getString("title"), rs.getString("thumbnail_url"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private SearchAnnouncementResponse mapAnnouncement(ResultSet rs, int rowNum) throws SQLException {
        return new SearchAnnouncementResponse(
                rs.getObject("id", UUID.class), rs.getObject("space_id", UUID.class),
                rs.getString("space_name"), rs.getString("title"), preview(rs.getString("content")),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private QuestionRow mapQuestionRow(ResultSet rs, int rowNum) throws SQLException {
        return new QuestionRow(
                rs.getObject("id", UUID.class), rs.getObject("space_id", UUID.class),
                rs.getString("space_name"), rs.getString("title"), rs.getString("content"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private String preview(String content) {
        if (content == null || content.length() <= CONTENT_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, CONTENT_PREVIEW_LENGTH) + "...";
    }

    private record QuestionRow(
            UUID questionId, UUID spaceId, String spaceName, String title,
            String content, OffsetDateTime createdAt) {
    }
}
