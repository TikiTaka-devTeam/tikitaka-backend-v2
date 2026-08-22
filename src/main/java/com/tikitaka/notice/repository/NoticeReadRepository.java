package com.tikitaka.notice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.notice.entity.NoticeRead;
import com.tikitaka.notice.entity.NoticeReadId;

public interface NoticeReadRepository
        extends JpaRepository<NoticeRead, NoticeReadId> {

    Optional<NoticeRead> findByNoticeIdAndUserId(
            UUID noticeId,
            UUID userId
    );

    boolean existsByNoticeIdAndUserId(
            UUID noticeId,
            UUID userId
    );

    long countByUserIdAndNoticeSpaceId(
            UUID userId,
            UUID spaceId
    );

    void deleteAllByNoticeId(UUID noticeId);
}