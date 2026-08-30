package com.tikitaka.notice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NoticeDetailResponse(
        @JsonProperty("notice_id") UUID noticeId,
        String title,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("author_name") String authorName,
        @JsonProperty("view_count") int viewCount,
        @JsonProperty("is_read") boolean read,
        @JsonProperty("read_at") Instant readAt,
        String content,
        List<NoticeFileResponse> files
) {}
