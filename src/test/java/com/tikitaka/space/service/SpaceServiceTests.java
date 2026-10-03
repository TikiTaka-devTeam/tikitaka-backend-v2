package com.tikitaka.space.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tikitaka.notification.service.NotificationService;
import com.tikitaka.space.dto.response.SpaceListResponse;
import com.tikitaka.space.entity.*;
import com.tikitaka.space.repository.*;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;

import tools.jackson.databind.ObjectMapper;

class SpaceServiceTests {
    private final SpaceMemberRepository members = mock(SpaceMemberRepository.class);
    private final SpaceMemberPermissionRepository permissions = mock(SpaceMemberPermissionRepository.class);
    private final SpaceService service = new SpaceService(
            mock(SpaceRepository.class), members, mock(ScheduleRepository.class),
            mock(NotificationService.class), permissions);
    private final User user = mock(User.class);

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "ARCHIVED"})
    void returnsOnlyPermissionsAssignedToAssistantInThisSpace(String status) {
        SpaceMember member = member(status, SpaceMemberRole.ASSISTANT);
        when(permissions.findAllBySpaceMemberId(member.getId())).thenReturn(List.of(
                SpaceMemberPermission.create(member, PermissionType.QUESTION_MANAGE),
                SpaceMemberPermission.create(member, PermissionType.LECTURE_MATERIAL_MANAGE)));

        SpaceListResponse response = service.getSpaces(user, status).spaces().get(0);

        assertThat(response.role()).isEqualTo(SpaceMemberRole.ASSISTANT);
        assertThat(response.permissions()).containsExactly(
                PermissionType.LECTURE_MATERIAL_MANAGE, PermissionType.QUESTION_MANAGE);
        assertThat(response.spaceCode()).isNull();
        verify(permissions).findAllBySpaceMemberId(member.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "ARCHIVED"})
    void returnsStudentRoleAndEmptyPermissions(String status) {
        member(status, SpaceMemberRole.STUDENT);

        SpaceListResponse response = service.getSpaces(user, status).spaces().get(0);

        assertThat(response.role()).isEqualTo(SpaceMemberRole.STUDENT);
        assertThat(response.permissions()).isEmpty();
        assertThat(new ObjectMapper().valueToTree(response).has("permissions")).isTrue();
        verifyNoInteractions(permissions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "ARCHIVED"})
    void assistantWithoutPermissionsDoesNotReceiveManagementPermissions(String status) {
        SpaceMember member = member(status, SpaceMemberRole.ASSISTANT);
        when(permissions.findAllBySpaceMemberId(member.getId())).thenReturn(List.of());

        SpaceListResponse response = service.getSpaces(user, status).spaces().get(0);

        assertThat(response.role()).isEqualTo(SpaceMemberRole.ASSISTANT);
        assertThat(response.permissions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "ARCHIVED"})
    void preservesProfessorResponseWithoutNewFields(String status) {
        member(status, SpaceMemberRole.PROFESSOR);

        SpaceListResponse response = service.getSpaces(user, status).spaces().get(0);
        var json = new ObjectMapper().valueToTree(response);

        assertThat(response.spaceCode()).isEqualTo("A1B2C3D4");
        assertThat(json.has("role")).isFalse();
        assertThat(json.has("permissions")).isFalse();
        verifyNoInteractions(permissions);
    }

    private SpaceMember member(String status, SpaceMemberRole role) {
        UUID userId = UUID.randomUUID();
        Space space = mock(Space.class);
        SpaceMember member = mock(SpaceMember.class);
        when(user.getId()).thenReturn(userId);
        when(user.getAccountType()).thenReturn(
                role == SpaceMemberRole.PROFESSOR ? AccountType.PROFESSOR : AccountType.STUDENT);
        when(space.getId()).thenReturn(UUID.randomUUID());
        when(space.getCreatedAt()).thenReturn(Instant.parse("2026-10-03T00:00:00Z"));
        when(space.isActiveStatus()).thenReturn(status.equals("ACTIVE"));
        when(space.getSpaceCode()).thenReturn("A1B2C3D4");
        when(member.getId()).thenReturn(UUID.randomUUID());
        when(member.getSpace()).thenReturn(space);
        when(member.getRole()).thenReturn(role);
        when(members.findAllByUserIdAndStatusAndRemovedAtIsNull(userId, SpaceMemberStatus.APPROVED))
                .thenReturn(List.of(member));
        return member;
    }
}
