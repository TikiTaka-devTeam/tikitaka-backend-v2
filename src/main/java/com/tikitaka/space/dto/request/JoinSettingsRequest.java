package com.tikitaka.space.dto.request;

import jakarta.validation.constraints.NotNull;

public record JoinSettingsRequest(
        @NotNull
        Boolean autoApprove
) {
}
