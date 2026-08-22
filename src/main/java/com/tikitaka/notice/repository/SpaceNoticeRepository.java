package com.tikitaka.notice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.notice.entity.SpaceNotice;

public interface SpaceNoticeRepository
        extends JpaRepository<SpaceNotice, UUID> {

    List<SpaceNotice> findAllBySpaceIdOrderByCreatedAtDesc(
            UUID spaceId
    );
}