package com.tikitaka.space.service;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.S3Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import com.tikitaka.space.dto.request.JoinRequestActionRequest;
import com.tikitaka.space.dto.request.JoinSettingsRequest;
import com.tikitaka.space.dto.request.RolePermissionsRequest;
import com.tikitaka.space.dto.response.InviteCodeResponse;
import com.tikitaka.space.dto.response.JoinRequestActionResponse;
import com.tikitaka.space.dto.response.JoinRequestListResponse;
import com.tikitaka.space.dto.response.JoinRequestResponse;
import com.tikitaka.space.dto.response.JoinSettingsResponse;
import com.tikitaka.space.dto.response.MemberDetailResponse;
import com.tikitaka.space.dto.response.MemberListItemResponse;
import com.tikitaka.space.dto.response.MemberListResponse;
import com.tikitaka.space.dto.response.RolePermissionsResponse;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberPermission;
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

    private final ObjectProvider<S3Service> s3ServiceProvider;
    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;
    private final ApplicationEventPublisher eventPublisher;

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
                                        profileImageUrl(member.getUser().getProfileUrl())
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
                                        profileImageUrl(member.getUser().getProfileUrl()),
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

    /**
     * MBR-006
     * 초대 코드 조회
     */
    public InviteCodeResponse getInviteCode(
            UUID spaceId,
            User currentUser
    ) {
        requireMemberManagePermission(
                spaceId,
                currentUser
        );

        Space space =
                getSpace(spaceId);

        return new InviteCodeResponse(
                space.getId(),
                space.getSpaceCode(),
                space.isAutoApprove()
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

            removeAndPublish(spaceId, target);
            return;
        }

        if (actor.getRole()
                == SpaceMemberRole.ASSISTANT
                && hasMemberManagePermission(actor)
                && target.getRole()
                == SpaceMemberRole.STUDENT) {

            removeAndPublish(spaceId, target);
            return;
        }

        throw new BusinessException(
                SpaceMemberErrorCode.MEMBER_MANAGE_FORBIDDEN
        );
    }

    private void removeAndPublish(UUID spaceId, SpaceMember target) {
        target.remove(Instant.now());
        eventPublisher.publishEvent(new SpaceMemberRemovedEvent(
                spaceId, target.getUser().getId()));
    }

    /**
     * MBR-009
     * 멤버 역할 및 조교 세부 권한 최종 저장
     */
    @Transactional
    public RolePermissionsResponse updateRolePermissions(
            UUID spaceId,
            UUID memberId,
            RolePermissionsRequest request,
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

        if (target.getRole() == SpaceMemberRole.PROFESSOR
                || request.role() == SpaceMemberRole.PROFESSOR) {

            throw new BusinessException(
                    SpaceMemberErrorCode.PROFESSOR_ROLE_CHANGE_FORBIDDEN
            );
        }

        if (request.role() == SpaceMemberRole.STUDENT
                && !request.permissions().isEmpty()) {

            throw new BusinessException(
                    SpaceMemberErrorCode.STUDENT_PERMISSION_NOT_ALLOWED
            );
        }

        List<PermissionType> permissions =
                request.permissions()
                        .stream()
                        .distinct()
                        .toList();

        target.updateRole(
                request.role()
        );

        permissionRepository.deleteAllBySpaceMemberId(
                target.getId()
        );

        permissionRepository.flush();

        if (request.role() == SpaceMemberRole.ASSISTANT) {

            List<SpaceMemberPermission> permissionEntities =
                    permissions.stream()
                            .map(permission ->
                                    SpaceMemberPermission.create(
                                            target,
                                            permission
                                    )
                            )
                            .toList();

            permissionRepository.saveAll(
                    permissionEntities
            );
        }

        return new RolePermissionsResponse(
                target.getId(),
                target.getRole(),
                request.role() == SpaceMemberRole.ASSISTANT
                        ? permissions
                        : List.of()
        );
    }

    /**
     * MBR-010
     * 조교 권한 조회
     */
    public RolePermissionsResponse getPermissions(
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

        boolean professor =
                actor.getRole() == SpaceMemberRole.PROFESSOR;

        boolean sameAssistant =
                actor.getId().equals(target.getId())
                        && actor.getRole() == SpaceMemberRole.ASSISTANT;

        if (!professor && !sameAssistant) {

            throw new BusinessException(
                    SpaceMemberErrorCode.PERMISSION_VIEW_FORBIDDEN
            );
        }

        List<PermissionType> permissions =
                permissionRepository
                        .findAllBySpaceMemberId(
                                target.getId()
                        )
                        .stream()
                        .map(SpaceMemberPermission::getPermission)
                        .sorted(
                                Comparator.comparingInt(
                                        PermissionType::ordinal
                                )
                        )
                        .toList();

        return new RolePermissionsResponse(
                target.getId(),
                target.getRole(),
                permissions
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

    private String profileImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        S3Service s3Service = s3ServiceProvider.getIfAvailable();
        return s3Service == null ? url : s3Service.presignedProfileUrl(url);
    }
}
