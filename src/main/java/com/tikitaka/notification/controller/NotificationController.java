package com.tikitaka.notification.controller;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.notification.dto.response.NotificationListResponse;
import com.tikitaka.notification.dto.response.NotificationReadAllResponse;
import com.tikitaka.notification.dto.response.NotificationReadResponse;
import com.tikitaka.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "NTF-001 알림 목록 최신순 조회")
    @GetMapping("/api/v1/notifications")
    public NotificationListResponse getNotifications(
            @RequestParam(name = "is_read", required = false) Boolean isRead,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return notificationService.getNotifications(
                isRead,
                cursor,
                size,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(summary = "NTF-002 개별 알림 읽음 처리")
    @PatchMapping("/api/v1/notifications/{notificationId}/read")
    public NotificationReadResponse readNotification(
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        return notificationService.readNotification(
                notificationId,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(summary = "NTF-003 모든 알림 읽음 처리")
    @PatchMapping("/api/v1/notifications/read-all")
    public NotificationReadAllResponse readAllNotifications(Authentication authentication) {
        return notificationService.readAllNotifications(
                currentUserResolver.resolve(authentication)
        );
    }
}
