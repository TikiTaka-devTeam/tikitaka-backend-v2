package com.tikitaka.systemnotice.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.systemnotice.entity.SystemNotice;

public interface SystemNoticeRepository
        extends JpaRepository<SystemNotice, UUID> {

    @Query("""
            select sn
            from SystemNotice sn
            order by sn.createdAt desc, sn.id desc
            """)
    List<SystemNotice> findFirstPage(
            Pageable pageable
    );

    @Query("""
            select sn
            from SystemNotice sn
            where sn.createdAt < :createdAt
               or (
                    sn.createdAt = :createdAt
                    and sn.id < :id
               )
            order by sn.createdAt desc, sn.id desc
            """)
    List<SystemNotice> findNextPage(
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable pageable
    );
}