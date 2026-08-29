package com.tikitaka.space.repository;

import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface SpaceMemberPermissionRepository
        extends Repository<SpaceMember, UUID> {

    boolean existsBySpaceMemberIdAndPermission(
            UUID spaceMemberId,
            PermissionType permission
    );
}