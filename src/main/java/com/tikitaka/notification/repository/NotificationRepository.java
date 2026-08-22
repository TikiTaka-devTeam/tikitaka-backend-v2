package com.tikitaka.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.notification.entity.Notification;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID userId,
            Instant createdAfter
    );

    List<Notification> findAllByUserIdAndReadFalse(
            UUID userId
    );

    long countByUserIdAndReadFalseAndCreatedAtAfter(
            UUID userId,
            Instant createdAfter
    );

    boolean existsByIdAndUserId(
            UUID id,
            UUID userId
    );
}