package com.tikitaka.notice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.notice.entity.NoticeFile;

public interface NoticeFileRepository
        extends JpaRepository<NoticeFile, UUID> {

    List<NoticeFile> findAllByNoticeId(UUID noticeId);

    void deleteAllByNoticeId(UUID noticeId);
}