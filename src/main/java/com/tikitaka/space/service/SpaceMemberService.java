package com.tikitaka.space.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.common.exception.BusinessException;
import com.tikitaka.space.dto.request.RolePermissionsRequest;
import com.tikitaka.space.dto.response.InviteCodeResponse;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceMemberService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;

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

        Space space = getSpace(spaceId);

        return new InviteCodeResponse(
                space.getId(),
                space.getSpaceCode(),
                space.isAutoApprove()
        );
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
        Space space = getSpace(spaceId);

        // 교수만 역할/권한 변경 가능
        if (!space.getProfessor()
                .getId()
                .equals(currentUser.getId())) {

            throw new BusinessException(
                    SpaceMemberErrorCode.PROFESSOR_ONLY
            );
        }

        SpaceMember target = getMemberInSpace(
                spaceId,
                memberId
        );

        // 승인된 현재 멤버만 수정 가능
        if (target.getStatus() != SpaceMemberStatus.APPROVED
                || target.getRemovedAt() != null) {

            throw new BusinessException(
                    SpaceMemberErrorCode.MEMBER_NOT_FOUND
            );
        }

        // 교수 역할은 변경 불가
        if (target.getRole() == SpaceMemberRole.PROFESSOR
                || request.role() == SpaceMemberRole.PROFESSOR) {

            throw new BusinessException(
                    SpaceMemberErrorCode.PROFESSOR_ROLE_CHANGE_FORBIDDEN
            );
        }

        // 학생에게 조교 권한을 줄 수 없음
        if (request.role() == SpaceMemberRole.STUDENT
                && !request.permissions().isEmpty()) {

            throw new BusinessException(
                    SpaceMemberErrorCode.STUDENT_PERMISSION_NOT_ALLOWED
            );
        }

        List<PermissionType> permissions = request.permissions()
                .stream()
                .distinct()
                .toList();

        target.updateRole(
                request.role()
        );

        // PUT이므로 기존 권한을 전부 삭제하고 최종 상태로 다시 저장
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
        SpaceMember actor = getApprovedMember(
                spaceId,
                currentUser.getId()
        );

        SpaceMember target = getMemberInSpace(
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

        // 교수 또는 해당 조교 본인만 조회 가능
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

    /**
     * Space 조회
     */
    private Space getSpace(
            UUID spaceId
    ) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() ->
                        new BusinessException(
                                SpaceMemberErrorCode.SPACE_NOT_FOUND
                        )
                );
    }

    /**
     * 특정 Space의 멤버 조회
     */
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

    /**
     * 승인된 멤버 조회
     */
    private SpaceMember getApprovedMember(
            UUID spaceId,
            UUID userId
    ) {
        SpaceMember member =
                spaceMemberRepository
                        .findBySpaceIdAndUserId(
                                spaceId,
                                userId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        SpaceMemberErrorCode.MEMBER_NOT_FOUND
                                )
                        );

        if (member.getStatus() != SpaceMemberStatus.APPROVED
                || member.getRemovedAt() != null) {

            throw new BusinessException(
                    SpaceMemberErrorCode.MEMBER_NOT_FOUND
            );
        }

        return member;
    }

    /**
     * 교수 또는 MEMBER_MANAGE 권한이 있는 조교인지 확인
     */
    private void requireMemberManagePermission(
            UUID spaceId,
            User currentUser
    ) {
        SpaceMember member = getApprovedMember(
                spaceId,
                currentUser.getId()
        );

        if (member.getRole() == SpaceMemberRole.PROFESSOR) {
            return;
        }

        if (member.getRole() != SpaceMemberRole.ASSISTANT) {
            throw new BusinessException(
                    SpaceMemberErrorCode.MEMBER_MANAGE_FORBIDDEN
            );
        }

        boolean hasPermission =
                permissionRepository
                        .existsBySpaceMemberIdAndPermission(
                                member.getId(),
                                PermissionType.MEMBER_MANAGE
                        );

        if (!hasPermission) {
            throw new BusinessException(
                    SpaceMemberErrorCode.MEMBER_MANAGE_FORBIDDEN
            );
        }
    }
}