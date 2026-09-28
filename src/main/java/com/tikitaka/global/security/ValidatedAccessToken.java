package com.tikitaka.global.security;

import java.time.Instant;
import java.util.UUID;

public record ValidatedAccessToken(UUID userId, Instant expiresAt) {
}
