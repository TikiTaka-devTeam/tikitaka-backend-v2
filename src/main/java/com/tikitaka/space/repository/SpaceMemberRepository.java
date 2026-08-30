package com.tikitaka.space.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberStatus;

public interface SpaceMemberRepository
        extends JpaRepository<SpaceMember, UUID> {

    List<SpaceMember>
    findAllByUserIdAndStatusAndRemovedAtIsNull(
            UUID userId,
            SpaceMemberStatus status
    );

    List<SpaceMember>
    findAllBySpaceIdAndStatusAndRemovedAtIsNull(
            UUID spaceId,
            SpaceMemberStatus status
    );

    Optional<SpaceMember>
    findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
            UUID spaceId,
            UUID userId,
            SpaceMemberStatus status
    );

    boolean existsBySpaceIdAndUserIdAndStatusInAndRemovedAtIsNull(
            UUID spaceId,
            UUID userId,
            List<SpaceMemberStatus> statuses
    );

    long countByUserIdAndRemovedAtIsNull(
            UUID userId
    );

    Optional<SpaceMember> findByIdAndSpaceId(
            UUID id,
            UUID spaceId
    );

    // 내보내진 기존 멤버 행 조회
    Optional<SpaceMember>
    findFirstBySpaceIdAndUserIdAndRemovedAtIsNotNullOrderByRemovedAtDesc(
            UUID spaceId,
            UUID userId
    );
}