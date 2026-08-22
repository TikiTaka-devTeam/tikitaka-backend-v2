package com.tikitaka.space.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMemberPermission;

public interface SpaceMemberPermissionRepository
        extends JpaRepository<SpaceMemberPermission, UUID> {

    List<SpaceMemberPermission> findAllBySpaceMemberId(UUID spaceMemberId);

    boolean existsBySpaceMemberIdAndPermission(
            UUID spaceMemberId,
            PermissionType permission
    );

    void deleteAllBySpaceMemberId(UUID spaceMemberId);
}