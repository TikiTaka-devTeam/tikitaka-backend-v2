package com.tikitaka.notice.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NoticeListItemResponse(
        @JsonProperty("notice_id") UUID noticeId,
        String title,
        @JsonProperty("content_preview") String contentPreview,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("is_read") boolean read
) {}
