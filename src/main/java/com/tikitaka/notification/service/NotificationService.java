package com.tikitaka.notification.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.notification.dto.response.NotificationItemResponse;
import com.tikitaka.notification.dto.response.NotificationListResponse;
import com.tikitaka.notification.dto.response.NotificationReadAllResponse;
import com.tikitaka.notification.dto.response.NotificationReadResponse;
import com.tikitaka.notification.entity.Notification;
import com.tikitaka.notification.entity.NotificationType;
import com.tikitaka.notification.exception.NotificationErrorCode;
import com.tikitaka.notification.repository.NotificationRepository;
import com.tikitaka.push.dto.response.WebPushPayload;
import com.tikitaka.push.service.WebPushService;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
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
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;
    private final CursorCodec cursorCodec;
    private final WebPushService webPushService;

    // NTF-001
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
        List<Notification> page = hasNext
                ? fetched.subList(0, pageSize)
                : fetched;

        String nextCursor = null;

        if (hasNext && !page.isEmpty()) {
            Notification last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(
                    new NotificationCursor(
                            last.getCreatedAt(),
                            last.getId()
                    )
            );
        }

        return new NotificationListResponse(
                page.stream()
                        .map(NotificationItemResponse::from)
                        .toList(),
                nextCursor,
                hasNext
        );
    }

    // NTF-002
    @Transactional
    public NotificationReadResponse readNotification(
            UUID notificationId,
            User currentUser
    ) {
        Notification notification = notificationRepository
                .findByIdAndUserId(
                        notificationId,
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                NotificationErrorCode.NOTIFICATION_NOT_FOUND
                        )
                );

        notification.markAsRead();

        return new NotificationReadResponse(
                notification.getId(),
                notification.isRead()
        );
    }

    // NTF-003
    @Transactional
    public NotificationReadAllResponse readAllNotifications(
            User currentUser
    ) {
        int updatedCount = notificationRepository.markAllAsRead(
                currentUser.getId(),
                Instant.now()
        );

        return new NotificationReadAllResponse(updatedCount);
    }

    // NOTICE_CREATED: 학생 + APPROVED 조교
    @Transactional
    public void createNoticeCreatedNotification(
            Space space,
            UUID noticeId
    ) {
        String message = space.getSpaceName()
                + "에 새로운 공지사항이 등록되었습니다.";

        createParticipantNotifications(
                space,
                NotificationType.NOTICE_CREATED,
                message,
                noticeId
        );
    }

    // DOCUMENT_UPLOADED: 학생 + APPROVED 조교
    @Transactional
    public void createDocumentUploadedNotification(
            Space space,
            UUID documentId
    ) {
        String message = space.getSpaceName()
                + "에 새로운 강의자료가 업로드되었습니다.";

        createParticipantNotifications(
                space,
                NotificationType.DOCUMENT_UPLOADED,
                message,
                documentId
        );
    }

    // ASSIGNMENT_CLOSED: 교수 + ASSIGNMENT_MANAGE 권한 조교
    @Transactional
    public void createAssignmentClosedNotification(
            Space space,
            UUID assignmentId
    ) {
        String message = space.getSpaceName()
                + "의 과제가 마감되었습니다.";

        createManagerNotifications(
                space,
                NotificationType.ASSIGNMENT_CLOSED,
                PermissionType.ASSIGNMENT_MANAGE,
                message,
                assignmentId,
                DuplicatePolicy.SKIP_EXISTING
        );
    }

    // SPACE_JOIN_REQUESTED: 교수 + MEMBER_MANAGE 권한 조교
    @Transactional
    public void createSpaceJoinRequestedNotification(
            Space space,
            UUID spaceMemberId
    ) {
        String message = space.getSpaceName()
                + "에 새로운 참여 요청이 있습니다.";

        createManagerNotifications(
                space,
                NotificationType.SPACE_JOIN_REQUESTED,
                PermissionType.MEMBER_MANAGE,
                message,
                spaceMemberId,
                DuplicatePolicy.CREATE_ALWAYS
        );
    }

    private void createParticipantNotifications(
            Space space,
            NotificationType type,
            String message,
            UUID targetId
    ) {
        List<SpaceMember> members = spaceMemberRepository
                .findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                        space.getId(),
                        SpaceMemberStatus.APPROVED
                );

        for (SpaceMember member : members) {
            if (member.getRole() != SpaceMemberRole.STUDENT
                    && member.getRole() != SpaceMemberRole.ASSISTANT) {
                continue;
            }

            saveIfAbsent(
                    member.getUser(),
                    space,
                    type,
                    message,
                    targetId
            );
        }
    }

    private void createManagerNotifications(
            Space space,
            NotificationType type,
            PermissionType assistantPermission,
            String message,
            UUID targetId,
            DuplicatePolicy duplicatePolicy
    ) {
        List<SpaceMember> members = spaceMemberRepository
                .findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                        space.getId(),
                        SpaceMemberStatus.APPROVED
                );

        for (SpaceMember member : members) {
            boolean professor = member.getRole() == SpaceMemberRole.PROFESSOR;
            boolean permittedAssistant =
                    member.getRole() == SpaceMemberRole.ASSISTANT
                            && permissionRepository
                                    .existsBySpaceMemberIdAndPermission(
                                            member.getId(),
                                            assistantPermission
                                    );

            if (!professor && !permittedAssistant) {
                continue;
            }

            if (duplicatePolicy == DuplicatePolicy.CREATE_ALWAYS) {
                save(
                        member.getUser(),
                        space,
                        type,
                        message,
                        targetId
                );
            } else {
                saveIfAbsent(
                        member.getUser(),
                        space,
                        type,
                        message,
                        targetId
                );
            }
        }
    }

    private void saveIfAbsent(
            User user,
            Space space,
            NotificationType type,
            String message,
            UUID targetId
    ) {
        boolean exists = notificationRepository
                .existsByUserIdAndTypeAndTargetId(
                        user.getId(),
                        type,
                        targetId
                );

        if (exists) {
            return;
        }

        save(
                user,
                space,
                type,
                message,
                targetId
        );
    }

    private void save(
            User user,
            Space space,
            NotificationType type,
            String message,
            UUID targetId
    ) {

        Notification notification = notificationRepository.save(
                Notification.create(
                        user,
                        space,
                        type,
                        message,
                        targetId
                )
        );

        sendPushAfterCommit(
                user.getId(),
                WebPushPayload.from(notification)
        );
    }

    private void sendPushAfterCommit(
            UUID userId,
            WebPushPayload payload
    ) {
        Runnable pushTask = () ->
                webPushService.sendToUser(userId, payload);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            pushTask.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        pushTask.run();
                    }
                }
        );
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    public record NotificationCursor(
            Instant createdAt,
            UUID id
    ) {
    }

    private enum DuplicatePolicy {
        SKIP_EXISTING,
        CREATE_ALWAYS
    }
}
