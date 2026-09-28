package com.tikitaka.space.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tikitaka.global.s3.S3Service;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.space.repository.SpaceRepository;
import com.tikitaka.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

class SpaceMemberServiceTests {

    @Test
    void publishesRemovalEventForProfessorApprovedRemoval() {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Service> s3 = mock(ObjectProvider.class);
        SpaceRepository spaces = mock(SpaceRepository.class);
        SpaceMemberRepository members = mock(SpaceMemberRepository.class);
        SpaceMemberPermissionRepository permissions = mock(SpaceMemberPermissionRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        SpaceMemberService service = new SpaceMemberService(
                s3, spaces, members, permissions, events);
        UUID spaceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        User professor = mock(User.class);
        User removedUser = mock(User.class);
        SpaceMember actor = mock(SpaceMember.class);
        SpaceMember target = mock(SpaceMember.class);
        when(professor.getId()).thenReturn(professorId);
        when(removedUser.getId()).thenReturn(removedUserId);
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(actor.getRole()).thenReturn(SpaceMemberRole.PROFESSOR);
        when(target.getId()).thenReturn(memberId);
        when(target.getRole()).thenReturn(SpaceMemberRole.STUDENT);
        when(target.getStatus()).thenReturn(SpaceMemberStatus.APPROVED);
        when(target.getUser()).thenReturn(removedUser);
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                spaceId, professorId, SpaceMemberStatus.APPROVED))
                .thenReturn(Optional.of(actor));
        when(members.findByIdAndSpaceId(memberId, spaceId)).thenReturn(Optional.of(target));

        service.removeMember(spaceId, memberId, professor);

        verify(target).remove(any(Instant.class));
        ArgumentCaptor<SpaceMemberRemovedEvent> event =
                ArgumentCaptor.forClass(SpaceMemberRemovedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue())
                .isEqualTo(new SpaceMemberRemovedEvent(spaceId, removedUserId));
    }
}
