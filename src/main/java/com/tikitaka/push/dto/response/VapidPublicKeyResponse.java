package com.tikitaka.push.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VapidPublicKeyResponse(
        @JsonProperty("public_key") String publicKey
) {
}
