package com.tikitaka.notice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NoticeUpdateResponse(
        @JsonProperty("notice_id") UUID noticeId,
        String title,
        String content,
        List<NoticeFileResponse> files,
        @JsonProperty("updated_at") Instant updatedAt
) {}
