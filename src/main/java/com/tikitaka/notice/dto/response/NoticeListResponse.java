package com.tikitaka.notice.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NoticeListResponse(
        @JsonProperty("total_count") long totalCount,
        @JsonProperty("unread_count") long unreadCount,
        List<NoticeListItemResponse> notices,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_next") boolean hasNext
) {}
