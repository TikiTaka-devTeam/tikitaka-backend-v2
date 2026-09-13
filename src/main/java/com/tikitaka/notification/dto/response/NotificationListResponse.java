package com.tikitaka.notification.dto.response;

import java.util.List;

public record NotificationListResponse(
        List<NotificationItemResponse> notifications,
        String nextCursor,
        boolean hasNext
) {
}
