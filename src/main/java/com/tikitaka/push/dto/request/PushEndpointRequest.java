package com.tikitaka.push.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PushEndpointRequest(
        @NotBlank String endpoint
) {
}
