package com.tikitaka.global.security;

import java.io.Serializable;
import java.util.UUID;

public record AuthenticatedUser(UUID userId) implements Serializable {
}
