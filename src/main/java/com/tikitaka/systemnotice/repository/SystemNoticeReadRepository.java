package com.tikitaka.systemnotice.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.tikitaka.systemnotice.entity.*;

public interface SystemNoticeReadRepository extends JpaRepository<SystemNoticeRead, SystemNoticeReadId> {
    boolean existsBySystemNoticeIdAndUserId(UUID systemNoticeId, UUID userId);
    Optional<SystemNoticeRead> findBySystemNoticeIdAndUserId(UUID systemNoticeId, UUID userId);
    @Query("""
        select count(n) from SystemNotice n
        where not exists (
            select r.id from SystemNoticeRead r
            where r.systemNotice.id = n.id and r.user.id = :userId
        )
        """)
    long countUnread(@Param("userId") UUID userId);
}
