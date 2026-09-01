package com.tikitaka.notice.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NoticeCreateResponse(
        @JsonProperty("notice_id") UUID noticeId,
        String title,
        @JsonProperty("created_at") Instant createdAt
) {}
