package com.tikitaka.systemnotice.dto.response;
import java.time.Instant; import java.util.UUID; import com.fasterxml.jackson.annotation.JsonProperty;
public record SystemNoticeDetailResponse(
 @JsonProperty("system_notice_id") UUID systemNoticeId, String title, String content,
 @JsonProperty("is_important") boolean important,
 @JsonProperty("is_read") boolean read,
 @JsonProperty("read_at") Instant readAt,
 @JsonProperty("created_at") Instant createdAt) {}
