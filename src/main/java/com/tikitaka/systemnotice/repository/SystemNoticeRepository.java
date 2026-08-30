package com.tikitaka.systemnotice.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.tikitaka.systemnotice.entity.SystemNotice;

public interface SystemNoticeRepository extends JpaRepository<SystemNotice, UUID> {
    @Query("""
        select n from SystemNotice n
        where (:createdAt is null or n.createdAt < :createdAt or (n.createdAt = :createdAt and n.id < :id))
        order by n.createdAt desc, n.id desc
        """)
    List<SystemNotice> findPage(@Param("createdAt") Instant createdAt, @Param("id") UUID id, Pageable pageable);
}
