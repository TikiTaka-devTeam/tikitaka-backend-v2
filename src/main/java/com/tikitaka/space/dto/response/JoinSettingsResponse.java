package com.tikitaka.space.dto.response;

import java.util.UUID;

public record JoinSettingsResponse(
        UUID spaceId,
        boolean autoApprove
) {
}
