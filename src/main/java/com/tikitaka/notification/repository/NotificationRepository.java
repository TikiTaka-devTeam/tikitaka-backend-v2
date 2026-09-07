package com.tikitaka.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n
            from Notification n
            left join fetch n.space
            where n.user.id = :userId
              and n.createdAt >= :createdAfter
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findFirstPage(
            @Param("userId") UUID userId,
            @Param("createdAfter") Instant createdAfter,
            Pageable pageable
    );

    @Query("""
            select n
            from Notification n
            left join fetch n.space
            where n.user.id = :userId
              and n.createdAt >= :createdAfter
              and (
                    n.createdAt < :cursorCreatedAt
                    or (n.createdAt = :cursorCreatedAt and n.id < :cursorId)
              )
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findNextPage(
            @Param("userId") UUID userId,
            @Param("createdAfter") Instant createdAfter,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query("""
            select n
            from Notification n
            left join fetch n.space
            where n.user.id = :userId
              and n.read = :isRead
              and n.createdAt >= :createdAfter
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findFirstPageByRead(
            @Param("userId") UUID userId,
            @Param("isRead") boolean isRead,
            @Param("createdAfter") Instant createdAfter,
            Pageable pageable
    );

    @Query("""
            select n
            from Notification n
            left join fetch n.space
            where n.user.id = :userId
              and n.read = :isRead
              and n.createdAt >= :createdAfter
              and (
                    n.createdAt < :cursorCreatedAt
                    or (n.createdAt = :cursorCreatedAt and n.id < :cursorId)
              )
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findNextPageByRead(
            @Param("userId") UUID userId,
            @Param("isRead") boolean isRead,
            @Param("createdAfter") Instant createdAfter,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Notification n
            set n.read = true,
                n.readAt = :readAt
            where n.user.id = :userId
              and n.read = false
            """)
    int markAllAsRead(
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt
    );
}
