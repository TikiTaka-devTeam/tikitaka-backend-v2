package com.tikitaka.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;
import com.tikitaka.search.dto.response.SearchDocumentResponse;

class SearchServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void searchesCurrentThumbnailColumnAndReturnsPresignedUrl() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DocumentStorage documentStorage = mock(DocumentStorage.class);
        SearchService service = new SearchService(jdbcTemplate, documentStorage);

        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-30T00:00:00Z");
        String thumbnailKey = "documents/assets/document-id/thumbnail.png";
        String thumbnailUrl = "https://example.com/signed-thumbnail.png";
        AtomicReference<String> documentSql = new AtomicReference<>();

        when(documentStorage.presignedGetUrl(thumbnailKey))
                .thenReturn(thumbnailUrl);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (!sql.contains("FROM documents d")) {
                return List.of();
            }

            documentSql.set(sql);
            RowMapper<SearchDocumentResponse> rowMapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getObject("id", UUID.class)).thenReturn(documentId);
            when(resultSet.getObject("space_id", UUID.class)).thenReturn(spaceId);
            when(resultSet.getString("space_name")).thenReturn("Operating Systems");
            when(resultSet.getString("title")).thenReturn("CPU Scheduling");
            when(resultSet.getString("thumbnail_key")).thenReturn(thumbnailKey);
            when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));

            return List.of(rowMapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        );

        var response = service.search(userId, " scheduling ");

        assertThat(documentSql.get())
                .contains("d.thumbnail_key")
                .doesNotContain("d.thumbnail_url");
        assertThat(response.documents()).singleElement().satisfies(document -> {
            assertThat(document.documentId()).isEqualTo(documentId);
            assertThat(document.thumbnailUrl()).isEqualTo(thumbnailUrl);
        });
        verify(documentStorage).presignedGetUrl(thumbnailKey);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsBlankKeywordAsBadRequest() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DocumentStorage documentStorage = mock(DocumentStorage.class);
        SearchService service = new SearchService(jdbcTemplate, documentStorage);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.search(UUID.randomUUID(), "   "))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verifyNoInteractions(jdbcTemplate, documentStorage);
    }

    @Test
    void rejectsKeywordLongerThanDatabaseLimit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DocumentStorage documentStorage = mock(DocumentStorage.class);
        SearchService service = new SearchService(jdbcTemplate, documentStorage);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.search(UUID.randomUUID(), "a".repeat(256)))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verifyNoInteractions(jdbcTemplate, documentStorage);
    }
}
