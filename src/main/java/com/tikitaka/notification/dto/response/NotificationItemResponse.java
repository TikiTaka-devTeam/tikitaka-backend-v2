package com.tikitaka.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.notification.entity.Notification;
import com.tikitaka.notification.entity.NotificationType;

public record NotificationItemResponse(
        UUID notificationId,
        NotificationType type,
        String message,
        UUID spaceId,
        UUID targetId,
        boolean isRead,
        Instant createdAt
) {
    public static NotificationItemResponse from(Notification notification) {
        return new NotificationItemResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getSpace() == null ? null : notification.getSpace().getId(),
                notification.getTargetId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
