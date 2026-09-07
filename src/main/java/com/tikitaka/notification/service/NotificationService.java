package com.tikitaka.notification.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.notification.dto.response.NotificationItemResponse;
import com.tikitaka.notification.dto.response.NotificationListResponse;
import com.tikitaka.notification.dto.response.NotificationReadAllResponse;
import com.tikitaka.notification.dto.response.NotificationReadResponse;
import com.tikitaka.notification.entity.Notification;
import com.tikitaka.notification.exception.NotificationErrorCode;
import com.tikitaka.notification.repository.NotificationRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int RETENTION_DAYS = 30;

    private final NotificationRepository notificationRepository;
    private final CursorCodec cursorCodec;

    /** NTF-001 알림 목록 최신순 조회 */
    public NotificationListResponse getNotifications(
            Boolean isRead,
            String cursor,
            int size,
            User currentUser
    ) {
        int pageSize = normalizeSize(size);
        Instant createdAfter = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        NotificationCursor decoded = cursorCodec.decodeOrNull(cursor, NotificationCursor.class);

        List<Notification> fetched;

        if (decoded == null) {
            fetched = isRead == null
                    ? notificationRepository.findFirstPage(
                            currentUser.getId(),
                            createdAfter,
                            PageRequest.of(0, pageSize + 1)
                    )
                    : notificationRepository.findFirstPageByRead(
                            currentUser.getId(),
                            isRead,
                            createdAfter,
                            PageRequest.of(0, pageSize + 1)
                    );
        } else {
            fetched = isRead == null
                    ? notificationRepository.findNextPage(
                            currentUser.getId(),
                            createdAfter,
                            decoded.createdAt(),
                            decoded.id(),
                            PageRequest.of(0, pageSize + 1)
                    )
                    : notificationRepository.findNextPageByRead(
                            currentUser.getId(),
                            isRead,
                            createdAfter,
                            decoded.createdAt(),
                            decoded.id(),
                            PageRequest.of(0, pageSize + 1)
                    );
        }

        boolean hasNext = fetched.size() > pageSize;
        List<Notification> page = hasNext ? fetched.subList(0, pageSize) : fetched;

        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Notification last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(new NotificationCursor(last.getCreatedAt(), last.getId()));
        }

        return new NotificationListResponse(
                page.stream().map(NotificationItemResponse::from).toList(),
                nextCursor,
                hasNext
        );
    }

    /** NTF-002 개별 알림 읽음 처리 */
    @Transactional
    public NotificationReadResponse readNotification(UUID notificationId, User currentUser) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, currentUser.getId())
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();

        return new NotificationReadResponse(notification.getId(), notification.isRead());
    }

    /** NTF-003 모든 알림 읽음 처리 */
    @Transactional
    public NotificationReadAllResponse readAllNotifications(User currentUser) {
        int updatedCount = notificationRepository.markAllAsRead(
                currentUser.getId(),
                Instant.now()
        );
        return new NotificationReadAllResponse(updatedCount);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public record NotificationCursor(Instant createdAt, UUID id) {
    }
}
