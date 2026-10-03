package com.tikitaka.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SearchHistoryServiceTests {
    @Test
    void savesTrimmedKeywordForUserAndPrunesOldHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID userId = UUID.randomUUID();
        new SearchHistoryService(jdbc, mock(DocumentStorage.class))
                .saveRecentSearch(userId, " 자료 ");

        verify(jdbc).update(contains("ON CONFLICT (user_id, keyword)"),
                any(UUID.class), eq(userId), eq("자료"));
        verify(jdbc).update(contains("OFFSET 10"), eq(userId));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rejectsInvalidKeywordsWithoutWritingHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SearchHistoryService service = new SearchHistoryService(jdbc, mock(DocumentStorage.class));
        for (String keyword : new String[] {null, "", "   ", "가".repeat(256)}) {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> service.saveRecentSearch(UUID.randomUUID(), keyword))
                    .satisfies(exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsDocumentAndSlideUrlsAndRetainsQuestionsWithoutSlides() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        UUID userId = UUID.randomUUID();
        when(storage.presignedGetUrl("documents/thumbnail.png")).thenReturn("https://example.com/document");
        when(storage.presignedGetUrl("slides/thumbnail.png")).thenReturn("https://example.com/slide");
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper<Object> mapper = invocation.getArgument(1);
            assertThat((Object[]) invocation.getRawArguments()[2]).containsExactly(userId);
            assertThat(sql).contains("sm.status = 'APPROVED'", "sm.removed_at IS NULL", "LIMIT 3");
            if (sql.contains("FROM recent_document_views")) {
                assertThat(sql).contains("d.thumbnail_key").doesNotContain("d.thumbnail_url");
                return List.of(mapper.mapRow(row("documents/thumbnail.png"), 0));
            }
            assertThat(sql).contains("LEFT JOIN slides sl ON sl.id = q.slide_id", "q.is_deleted = FALSE");
            return List.of(mapper.mapRow(row("slides/thumbnail.png"), 0), mapper.mapRow(row(null), 1));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        var result = new SearchHistoryService(jdbc, storage).getRecentItems(userId);

        assertThat(result.documents()).singleElement().satisfies(document ->
                assertThat(document.thumbnailUrl()).isEqualTo("https://example.com/document"));
        assertThat(result.questions()).hasSize(2);
        assertThat(result.questions().get(0).thumbnailUrl()).isEqualTo("https://example.com/slide");
        assertThat(result.questions().get(1).thumbnailUrl()).isNull();
        verify(storage).presignedGetUrl("documents/thumbnail.png");
        verify(storage).presignedGetUrl("slides/thumbnail.png");
        verifyNoMoreInteractions(storage);
    }

    private ResultSet row(String thumbnailKey) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.getObject("space_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.getString("space_name")).thenReturn("운영체제");
        when(row.getString("title")).thenReturn("최근 항목");
        when(row.getString("thumbnail_key")).thenReturn(thumbnailKey);
        when(row.getTimestamp("viewed_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")));
        return row;
    }
}
