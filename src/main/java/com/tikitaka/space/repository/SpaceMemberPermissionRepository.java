package com.tikitaka.space.repository;

import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMemberPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpaceMemberPermissionRepository
        extends JpaRepository<SpaceMemberPermission, UUID> {

    boolean existsBySpaceMemberIdAndPermission(
            UUID spaceMemberId,
            PermissionType permission
    );

    List<SpaceMemberPermission> findAllBySpaceMemberId(
            UUID spaceMemberId
    );

    void deleteAllBySpaceMemberId(
            UUID spaceMemberId
    );
}