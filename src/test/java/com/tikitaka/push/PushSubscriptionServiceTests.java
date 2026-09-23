package com.tikitaka.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tikitaka.push.dto.request.PushEndpointRequest;
import com.tikitaka.push.dto.request.PushSubscriptionRequest;
import com.tikitaka.push.dto.response.PushSubscriptionResponse;
import com.tikitaka.push.entity.PushSubscription;
import com.tikitaka.push.repository.PushSubscriptionRepository;
import com.tikitaka.push.service.PushSubscriptionService;
import com.tikitaka.user.entity.User;

class PushSubscriptionServiceTests {

    private final PushSubscriptionRepository pushSubscriptionRepository =
            mock(PushSubscriptionRepository.class);
    private final PushSubscriptionService pushSubscriptionService =
            new PushSubscriptionService(pushSubscriptionRepository);

    @Test
    void upsertsSubscriptionByEndpointForCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-23T00:00:00Z");
        String endpoint = "https://push.example.test/subscriptions/1";
        User user = mock(User.class);
        PushSubscription subscription = mock(PushSubscription.class);
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                endpoint,
                new PushSubscriptionRequest.Keys("p256dh", "auth")
        );

        when(user.getId()).thenReturn(userId);
        when(pushSubscriptionRepository.findByEndpoint(endpoint))
                .thenReturn(Optional.of(subscription));
        when(subscription.getId()).thenReturn(subscriptionId);
        when(subscription.getCreatedAt()).thenReturn(createdAt);

        PushSubscriptionResponse response = pushSubscriptionService.subscribe(
                request,
                user
        );

        verify(pushSubscriptionRepository).upsertByEndpoint(
                userId,
                endpoint,
                "p256dh",
                "auth"
        );
        assertThat(response.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void unsubscribesCurrentUsersSubscriptionByEndpoint() {
        UUID userId = UUID.randomUUID();
        String endpoint = "https://push.example.test/subscriptions/1";
        User user = mock(User.class);
        PushSubscription subscription = mock(PushSubscription.class);

        when(user.getId()).thenReturn(userId);
        when(pushSubscriptionRepository.findByEndpointAndUserId(endpoint, userId))
                .thenReturn(Optional.of(subscription));

        pushSubscriptionService.unsubscribeByEndpoint(
                new PushEndpointRequest(endpoint),
                user
        );

        verify(pushSubscriptionRepository).delete(subscription);
    }
}
