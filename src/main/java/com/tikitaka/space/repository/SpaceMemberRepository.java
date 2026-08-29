package com.tikitaka.space.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberStatus;

public interface SpaceMemberRepository
        extends JpaRepository<SpaceMember, UUID> {

    /**
     * 사용자가 현재 참여 중인 멤버십 조회
     * removed_at이 있는 멤버는 제외한다.
     */
    List<SpaceMember> findAllByUserIdAndStatusAndRemovedAtIsNull(
            UUID userId,
            SpaceMemberStatus status
    );

    /**
     * Space의 현재 멤버 조회
     */
    List<SpaceMember> findAllBySpaceIdAndStatusAndRemovedAtIsNull(
            UUID spaceId,
            SpaceMemberStatus status
    );

    /**
     * 특정 Space에서 현재 사용자의 멤버십 조회
     */
    Optional<SpaceMember>
    findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
            UUID spaceId,
            UUID userId,
            SpaceMemberStatus status
    );

    /**
     * 특정 상태 중 하나인 현재 멤버십 존재 여부
     */
    boolean existsBySpaceIdAndUserIdAndStatusInAndRemovedAtIsNull(
            UUID spaceId,
            UUID userId,
            List<SpaceMemberStatus> statuses
    );

    /**
     * 현재 제거되지 않은 사용자의 Space 멤버십 개수
     */
    long countByUserIdAndRemovedAtIsNull(
            UUID userId
    );

    /**
     * 특정 Space의 멤버 조회
     */
    Optional<SpaceMember> findByIdAndSpaceId(
            UUID id,
            UUID spaceId
    );
}