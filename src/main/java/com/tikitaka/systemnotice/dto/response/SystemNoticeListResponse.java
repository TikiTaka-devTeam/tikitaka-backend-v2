package com.tikitaka.systemnotice.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
public record SystemNoticeListResponse(
 @JsonProperty("total_count") long totalCount,
 @JsonProperty("unread_count") long unreadCount,
 @JsonProperty("system_notices") List<SystemNoticeListItemResponse> systemNotices,
 @JsonProperty("next_cursor") String nextCursor,
 @JsonProperty("has_next") boolean hasNext) {}
