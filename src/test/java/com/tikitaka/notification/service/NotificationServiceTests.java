package com.tikitaka.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.notification.entity.Notification;
import com.tikitaka.notification.entity.NotificationType;
import com.tikitaka.notification.repository.NotificationRepository;
import com.tikitaka.push.dto.response.WebPushPayload;
import com.tikitaka.push.service.WebPushService;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

class NotificationServiceTests {

    private NotificationRepository notifications;
    private SpaceMemberRepository members;
    private WebPushService webPush;
    private NotificationService service;
    private SpaceMember member;
    private User user;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        members = mock(SpaceMemberRepository.class);
        webPush = mock(WebPushService.class);
        service = new NotificationService(
                notifications,
                members,
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

    @Test
    void assistantReceivesProfessorNotificationsAndNoStudentNotifications() {
        UUID userId = UUID.randomUUID();
        Space space = spaceWithManager(userId, SpaceMemberRole.ASSISTANT);
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                space.getId(), userId, SpaceMemberStatus.APPROVED
        )).thenReturn(Optional.of(member));

        service.createNoticeCreatedNotification(space, UUID.randomUUID());
        service.createDocumentUploadedNotification(space, UUID.randomUUID());
        service.createAssignmentClosedNotification(space, UUID.randomUUID());
        service.createSpaceJoinRequestedNotification(space, UUID.randomUUID());
        service.createQuestionCreatedNotification(space, UUID.randomUUID());
        service.createQuestionAnsweredNotification(user, space, UUID.randomUUID());

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(3)).save(saved.capture());
        assertEquals(List.of(
                NotificationType.ASSIGNMENT_CLOSED,
                NotificationType.SPACE_JOIN_REQUESTED,
                NotificationType.QUESTION_CREATED
        ), saved.getAllValues().stream().map(Notification::getType).toList());
    }

    @Test
    void returningToStudentSwitchesNewNotificationsToStudentTypes() {
        UUID userId = UUID.randomUUID();
        Space space = spaceWithManager(userId, SpaceMemberRole.ASSISTANT);

        service.createAssignmentClosedNotification(space, UUID.randomUUID());
        when(member.getRole()).thenReturn(SpaceMemberRole.STUDENT);
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                space.getId(), userId, SpaceMemberStatus.APPROVED
        )).thenReturn(Optional.of(member));

        service.createAssignmentClosedNotification(space, UUID.randomUUID());
        service.createNoticeCreatedNotification(space, UUID.randomUUID());
        service.createDocumentUploadedNotification(space, UUID.randomUUID());
        service.createQuestionAnsweredNotification(user, space, UUID.randomUUID());

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(4)).save(saved.capture());
        assertEquals(List.of(
                NotificationType.ASSIGNMENT_CLOSED,
                NotificationType.NOTICE_CREATED,
                NotificationType.DOCUMENT_UPLOADED,
                NotificationType.QUESTION_ANSWERED
        ), saved.getAllValues().stream().map(Notification::getType).toList());
    }

    private Space spaceWithManager(
            UUID userId,
            SpaceMemberRole role
    ) {
        UUID spaceId = UUID.randomUUID();
        Space space = mock(Space.class);
        member = mock(SpaceMember.class);
        user = mock(User.class);

        when(space.getId()).thenReturn(spaceId);
        when(space.getSpaceName()).thenReturn("테스트 Space");
        when(member.getRole()).thenReturn(role);
        when(member.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(members.findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                spaceId,
                SpaceMemberStatus.APPROVED
        )).thenReturn(List.of(member));

        return space;
    }
}
