package com.tikitaka.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.notification.entity.Notification;
import com.tikitaka.notification.entity.NotificationType;
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

class NotificationServiceTests {

    private NotificationRepository notifications;
    private SpaceMemberRepository members;
    private SpaceMemberPermissionRepository permissions;
    private WebPushService webPush;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        members = mock(SpaceMemberRepository.class);
        permissions = mock(SpaceMemberPermissionRepository.class);
        webPush = mock(WebPushService.class);
        service = new NotificationService(
                notifications,
                members,
                permissions,
                mock(CursorCodec.class),
                webPush
        );

        when(notifications.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsNotificationForEverySpaceJoinRequest() {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Space space = spaceWithManager(userId, SpaceMemberRole.PROFESSOR);

        when(notifications.existsByUserIdAndTypeAndTargetId(
                userId,
                NotificationType.SPACE_JOIN_REQUESTED,
                memberId
        )).thenReturn(true);

        service.createSpaceJoinRequestedNotification(space, memberId);

        verify(notifications, never()).existsByUserIdAndTypeAndTargetId(
                userId,
                NotificationType.SPACE_JOIN_REQUESTED,
                memberId
        );
        verify(notifications).save(any(Notification.class));
        verify(webPush).sendToUser(eq(userId), any(WebPushPayload.class));
    }

    @Test
    void keepsAssignmentClosedNotificationDeduplicated() {
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        Space space = spaceWithManager(userId, SpaceMemberRole.PROFESSOR);

        when(notifications.existsByUserIdAndTypeAndTargetId(
                userId,
                NotificationType.ASSIGNMENT_CLOSED,
                assignmentId
        )).thenReturn(true);

        service.createAssignmentClosedNotification(space, assignmentId);

        verify(notifications, never()).save(any(Notification.class));
        verify(webPush, never()).sendToUser(any(), any());
    }

    private Space spaceWithManager(
            UUID userId,
            SpaceMemberRole role
    ) {
        UUID spaceId = UUID.randomUUID();
        Space space = mock(Space.class);
        SpaceMember member = mock(SpaceMember.class);
        User user = mock(User.class);

        when(space.getId()).thenReturn(spaceId);
        when(space.getSpaceName()).thenReturn("테스트 Space");
        when(member.getRole()).thenReturn(role);
        when(member.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(members.findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                spaceId,
                SpaceMemberStatus.APPROVED
        )).thenReturn(List.of(member));

        if (role == SpaceMemberRole.ASSISTANT) {
            when(permissions.existsBySpaceMemberIdAndPermission(
                    member.getId(),
                    PermissionType.MEMBER_MANAGE
            )).thenReturn(true);
        }

        return space;
    }
}
