package com.tikitaka.notice.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.tikitaka.notice.entity.NoticeRead;
import com.tikitaka.notice.entity.NoticeReadId;

public interface NoticeReadRepository extends JpaRepository<NoticeRead, NoticeReadId> {
    Optional<NoticeRead> findByNoticeIdAndUserId(UUID noticeId, UUID userId);
    boolean existsByNoticeIdAndUserId(UUID noticeId, UUID userId);
    void deleteAllByNoticeId(UUID noticeId);

    @Query("""
        select count(n) from SpaceNotice n
        where n.space.id = :spaceId
          and not exists (
              select r.id from NoticeRead r
              where r.notice.id = n.id and r.user.id = :userId
          )
        """)
    long countUnread(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId);
}
