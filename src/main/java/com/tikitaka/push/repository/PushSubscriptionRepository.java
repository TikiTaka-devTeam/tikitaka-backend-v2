package com.tikitaka.push.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.push.entity.PushSubscription;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);

    Optional<PushSubscription> findByEndpointAndUserId(
            String endpoint,
            UUID userId
    );

    List<PushSubscription> findAllByUserId(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    INSERT INTO push_subscriptions (
                        id, user_id, endpoint, p256dh, auth, created_at, updated_at
                    )
                    VALUES (
                        gen_random_uuid(), :userId, :endpoint, :p256dh, :auth, NOW(), NOW()
                    )
                    ON CONFLICT (endpoint)
                    DO UPDATE SET
                        user_id = EXCLUDED.user_id,
                        p256dh = EXCLUDED.p256dh,
                        auth = EXCLUDED.auth,
                        updated_at = NOW()
                    """,
            nativeQuery = true
    )
    int upsertByEndpoint(
            @Param("userId") UUID userId,
            @Param("endpoint") String endpoint,
            @Param("p256dh") String p256dh,
            @Param("auth") String auth
    );
}
