package com.tikitaka.notice.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.notice.entity.SpaceNotice;

public interface SpaceNoticeRepository extends JpaRepository<SpaceNotice, UUID> {
    long countBySpaceId(UUID spaceId);

    @Query("""
        select n from SpaceNotice n
        where n.space.id = :spaceId
          and (:createdAt is null
               or n.createdAt < :createdAt
               or (n.createdAt = :createdAt and n.id < :id))
        order by n.createdAt desc, n.id desc
        """)
    List<SpaceNotice> findPage(
            @Param("spaceId") UUID spaceId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable pageable
    );
}
