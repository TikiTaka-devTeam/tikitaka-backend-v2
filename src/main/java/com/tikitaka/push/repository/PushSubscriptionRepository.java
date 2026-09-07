package com.tikitaka.push.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.push.entity.PushSubscription;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);

    List<PushSubscription> findAllByUserId(UUID userId);
}
