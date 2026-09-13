package com.tikitaka.notification.dto.response;

import java.util.UUID;

public record NotificationReadResponse(
        UUID notificationId,
        boolean isRead
) {
}
