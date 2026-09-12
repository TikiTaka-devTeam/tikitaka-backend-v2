package com.tikitaka.push.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.notification.entity.Notification;
import com.tikitaka.notification.entity.NotificationType;

public record WebPushPayload(
        @JsonProperty("notification_id") UUID notificationId,
        NotificationType type,
        String title,
        String message,
        @JsonProperty("space_id") UUID spaceId,
        @JsonProperty("target_id") UUID targetId
) {
    public static WebPushPayload from(Notification notification) {
        return new WebPushPayload(
                notification.getId(),
                notification.getType(),
                "Tikitaka",
                notification.getMessage(),
                notification.getSpace() == null ? null : notification.getSpace().getId(),
                notification.getTargetId()
        );
    }
}
