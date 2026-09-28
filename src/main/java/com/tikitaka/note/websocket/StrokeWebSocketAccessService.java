package com.tikitaka.note.websocket;

import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StrokeWebSocketAccessService {

    private final SlideRepository slideRepository;
    private final SpaceMemberRepository memberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public void requireSubscription(UUID userId, UUID spaceId, UUID slideId) {
        requireMember(userId, spaceId, slideId);
    }

    @Transactional(readOnly = true)
    public void requireSharedEdit(UUID userId, UUID spaceId, UUID slideId) {
        SpaceMember member = requireMember(userId, spaceId, slideId);
        boolean professor = member.getRole() == SpaceMemberRole.PROFESSOR;
        boolean permittedAssistant = member.getRole() == SpaceMemberRole.ASSISTANT
                && permissionRepository.existsBySpaceMemberIdAndPermission(
                        member.getId(), PermissionType.LECTURE_MATERIAL_MANAGE);
        if (!professor && !permittedAssistant) {
            denied();
        }
    }

    private SpaceMember requireMember(UUID userId, UUID spaceId, UUID slideId) {
        if (!slideRepository.existsByIdAndDocumentSpaceId(slideId, spaceId)) {
            return denied();
        }
        return memberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId, userId, SpaceMemberStatus.APPROVED)
                .orElseGet(this::denied);
    }

    private <T> T denied() {
        throw new MessageDeliveryException("WS_ACCESS_DENIED");
    }
}
