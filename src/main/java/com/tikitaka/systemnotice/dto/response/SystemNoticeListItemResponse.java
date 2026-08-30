package com.tikitaka.systemnotice.dto.response;
import java.time.Instant; import java.util.UUID; import com.fasterxml.jackson.annotation.JsonProperty;
public record SystemNoticeListItemResponse(
 @JsonProperty("system_notice_id") UUID systemNoticeId, String title,
 @JsonProperty("content_preview") String contentPreview,
 @JsonProperty("is_important") boolean important,
 @JsonProperty("is_read") boolean read,
 @JsonProperty("created_at") Instant createdAt) {}
