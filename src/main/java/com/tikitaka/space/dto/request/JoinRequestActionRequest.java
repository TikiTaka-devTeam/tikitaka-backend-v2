package com.tikitaka.space.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record JoinRequestActionRequest(
        @NotEmpty
        List<UUID> joinRequestIds
) {
}
