package com.tikitaka.space.service;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.space.dto.request.JoinRequestActionRequest;
import com.tikitaka.space.dto.request.JoinSettingsRequest;
import com.tikitaka.space.dto.response.JoinRequestActionResponse;
import com.tikitaka.space.dto.response.JoinRequestListResponse;
import com.tikitaka.space.dto.response.JoinRequestResponse;
import com.tikitaka.space.dto.response.JoinSettingsResponse;
import com.tikitaka.space.dto.response.MemberDetailResponse;
import com.tikitaka.space.dto.response.MemberListItemResponse;
import com.tikitaka.space.dto.response.MemberListResponse;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.exception.SpaceMemberErrorCode;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.space.repository.SpaceRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceMemberService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;

    public MemberListResponse getMembers(
            UUID spaceId,
            User currentUser
    ) {
        getApprovedMember(
                spaceId,
                currentUser.getId()
        );

        List<MemberListItemResponse> members =
                spaceMemberRepository
                        .findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                                spaceId,
                                SpaceMemberStatus.APPROVED
                        )
                        .stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                (SpaceMember member) ->
                                                        roleOrder(member.getRole())
                                        )
                                        .thenComparing(
                                                member ->
                                                        member.getUser().getName()
                                        )
                                        .thenComparing(
                                                SpaceMember::getId
                                        )
                        )
                        .map(member ->
                                new MemberListItemResponse(
                                        member.getId(),
                                        member.getRole(),
                                        member.getUser().getName(),
                                        member.getUser().getMemberIdNumber(),
                                        member.getUser().getProfileUrl()
                                )
                        )
                        .toList();

        return new MemberListResponse(
                members.size(),
                members
        );
    }

    public MemberDetailResponse getMemberDetail(
            UUID spaceId,
            UUID memberId,
            User currentUser
    ) {
        requireMemberManagePermission(
                spaceId,
                currentUser
        );

        SpaceMember target =
                getMemberInSpace(
                        spaceId,
                        memberId
                );

        if (target.getStatus() != SpaceMemberStatus.APPROVED
                || target.getRemovedAt() != null) {

            throw new BusinessException(
                    SpaceMemberErrorCode.MEMBER_NOT_FOUND
            );
        }

        User user =
                target.getUser();

        return new MemberDetailResponse(
                target.getId(),
                user.getName(),
                user.getUniv(),
                user.getEmail(),
                user.getMajor(),
                user.getMemberIdNumber(),
                target.getRole(),
                target.getApprovedAt()
        );
    }

    public JoinRequestListResponse getJoinRequests(
            UUID spaceId,
            User currentUser
    ) {
        requireMemberManagePermission(
                spaceId,
                currentUser
        );

        List<JoinRequestResponse> requests =
                spaceMemberRepository
                        .findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                                spaceId,
                                SpaceMemberStatus.PENDING
                        )
                        .stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                SpaceMember::getRequestedAt
                                        )
                                        .thenComparing(
                                                SpaceMember::getId
                                        )
                        )
                        .map(member ->
                                new JoinRequestResponse(
                                        member.getId(),
                                        member.getUser().getId(),
                                        member.getUser().getName(),
                                        member.getUser().getMemberIdNumber(),
                                        member.getUser().getProfileUrl(),
                                        member.getRequestedAt()
                                )
                        )
                        .toList();

        return new JoinRequestListResponse(
                requests
        );
    }

    @Transactional
    public JoinRequestActionResponse approveJoinRequests(
            UUID spaceId,
            JoinRequestActionRequest request,
            User currentUser
    ) {
        requireMemberManagePermission(
                spaceId,
                currentUser
        );

        List<SpaceMember> targets =
                getPendingRequests(
                        spaceId,
                        request.joinRequestIds()
                );

        Instant now =
                Instant.now();

        targets.forEach(
                member ->
                        member.approve(now)
        );

        return JoinRequestActionResponse.approved(
                targets.size()
        );
    }

    @Transactional
    public JoinRequestActionResponse denyJoinRequests(
            UUID spaceId,
            JoinRequestActionRequest request,
            User currentUser
    ) {
        requireMemberManagePermission(
                spaceId,
                currentUser
        );

        List<SpaceMember> targets =
                getPendingRequests(
                        spaceId,
                        request.joinRequestIds()
                );

        Instant now =
                Instant.now();

        targets.forEach(
                member ->
                        member.deny(now)
        );

        return JoinRequestActionResponse.denied(
                targets.size()
        );
    }

    @Transactional
    public JoinSettingsResponse updateJoinSettings(
            UUID spaceId,
            JoinSettingsRequest request,
            User currentUser
    ) {
        Space space =
                getSpace(spaceId);

        if (!space.getProfessor()
                .getId()
                .equals(currentUser.getId())) {

            throw new BusinessException(
                    SpaceMemberErrorCode.PROFESSOR_ONLY
            );
        }

        boolean autoApprove =
                request.autoApprove();

        space.updateAutoApprove(
                autoApprove
        );

        // 자동승인을 켜는 순간 기존 승인 대기 학생들도 모두 승인
        if (autoApprove) {

            List<SpaceMember> pendingMembers =
                    spaceMemberRepository
                            .findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                                    spaceId,
                                    SpaceMemberStatus.PENDING
                            );

            Instant now =
                    Instant.now();

            pendingMembers.forEach(
                    member ->
                            member.approve(now)
            );
        }

        return new JoinSettingsResponse(
                space.getId(),
                space.isAutoApprove()
        );
    }

    @Transactional
    public void removeMember(
            UUID spaceId,
            UUID memberId,
            User currentUser
    ) {
        SpaceMember actor =
                getApprovedMember(
                        spaceId,
                        currentUser.getId()
                );

        SpaceMember target =
                getMemberInSpace(
                        spaceId,
                        memberId
                );

        if (target.getStatus() != SpaceMemberStatus.APPROVED
                || target.getRemovedAt() != null) {

            throw new BusinessException(
                    SpaceMemberErrorCode.MEMBER_NOT_FOUND
            );
        }

        if (actor.getId()
                .equals(target.getId())) {

            throw new BusinessException(
                    SpaceMemberErrorCode.CANNOT_REMOVE_SELF
            );
        }

        if (target.getRole()
                == SpaceMemberRole.PROFESSOR) {

            throw new BusinessException(
                    SpaceMemberErrorCode.CANNOT_REMOVE_PROFESSOR
            );
        }

        if (actor.getRole()
                == SpaceMemberRole.PROFESSOR) {

            target.remove(
                    Instant.now()
            );

            return;
        }

        if (actor.getRole()
                == SpaceMemberRole.ASSISTANT
                && hasMemberManagePermission(actor)
                && target.getRole()
                == SpaceMemberRole.STUDENT) {

            target.remove(
                    Instant.now()
            );

            return;
        }

        throw new BusinessException(
                SpaceMemberErrorCode.MEMBER_MANAGE_FORBIDDEN
        );
    }

    private List<SpaceMember> getPendingRequests(
            UUID spaceId,
            List<UUID> requestIds
    ) {
        List<SpaceMember> targets =
                spaceMemberRepository
                        .findAllById(
                                requestIds
                        );

        if (targets.size()
                != requestIds.size()) {

            throw new BusinessException(
                    SpaceMemberErrorCode.JOIN_REQUEST_NOT_FOUND
            );
        }

        boolean invalid =
                targets.stream()
                        .anyMatch(member ->
                                !member.getSpace()
                                        .getId()
                                        .equals(spaceId)
                                        || member.getStatus()
                                        != SpaceMemberStatus.PENDING
                                        || member.getRemovedAt()
                                        != null
                        );

        if (invalid) {

            throw new BusinessException(
                    SpaceMemberErrorCode.INVALID_JOIN_REQUEST
            );
        }

        return targets;
    }

    private void requireMemberManagePermission(
            UUID spaceId,
            User currentUser
    ) {
        SpaceMember actor =
                getApprovedMember(
                        spaceId,
                        currentUser.getId()
                );

        if (actor.getRole()
                == SpaceMemberRole.PROFESSOR) {

            return;
        }

        if (actor.getRole()
                == SpaceMemberRole.ASSISTANT
                && hasMemberManagePermission(actor)) {

            return;
        }

        throw new BusinessException(
                SpaceMemberErrorCode.MEMBER_MANAGE_FORBIDDEN
        );
    }

    private boolean hasMemberManagePermission(
            SpaceMember member
    ) {
        return permissionRepository
                .existsBySpaceMemberIdAndPermission(
                        member.getId(),
                        PermissionType.MEMBER_MANAGE
                );
    }

    private SpaceMember getApprovedMember(
            UUID spaceId,
            UUID userId
    ) {
        return spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId,
                        userId,
                        SpaceMemberStatus.APPROVED
                )
                .orElseThrow(() ->
                        new BusinessException(
                                SpaceMemberErrorCode.SPACE_ACCESS_DENIED
                        )
                );
    }

    private SpaceMember getMemberInSpace(
            UUID spaceId,
            UUID memberId
    ) {
        return spaceMemberRepository
                .findByIdAndSpaceId(
                        memberId,
                        spaceId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                SpaceMemberErrorCode.MEMBER_NOT_FOUND
                        )
                );
    }

    private Space getSpace(
            UUID spaceId
    ) {
        return spaceRepository
                .findById(
                        spaceId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                SpaceMemberErrorCode.SPACE_NOT_FOUND
                        )
                );
    }

    private int roleOrder(
            SpaceMemberRole role
    ) {
        return switch (role) {
            case PROFESSOR -> 0;
            case ASSISTANT -> 1;
            case STUDENT -> 2;
        };
    }
}