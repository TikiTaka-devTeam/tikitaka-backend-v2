package com.tikitaka.space.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberStatus;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, UUID> {

    List<SpaceMember> findAllByUserIdAndStatus(UUID userId, SpaceMemberStatus status);

    List<SpaceMember> findAllBySpaceIdAndStatus(UUID spaceId, SpaceMemberStatus status);

    Optional<SpaceMember> findBySpaceIdAndUserIdAndStatus(
            UUID spaceId, UUID userId, SpaceMemberStatus status);

    boolean existsBySpaceIdAndUserIdAndStatusIn(
            UUID spaceId, UUID userId, List<SpaceMemberStatus> statuses);

    long countByUserId(UUID userId);
}
