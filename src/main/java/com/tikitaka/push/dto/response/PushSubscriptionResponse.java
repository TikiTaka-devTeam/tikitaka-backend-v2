package com.tikitaka.push.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PushSubscriptionResponse(
        @JsonProperty("subscription_id") UUID subscriptionId,
        @JsonProperty("created_at") Instant createdAt
) {
}
