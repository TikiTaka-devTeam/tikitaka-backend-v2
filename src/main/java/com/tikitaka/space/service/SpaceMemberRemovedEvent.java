package com.tikitaka.space.service;

import java.util.UUID;

public record SpaceMemberRemovedEvent(UUID spaceId, UUID userId) {
}
