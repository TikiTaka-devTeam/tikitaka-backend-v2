package com.tikitaka.search.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RecentSearchResponse(UUID searchId, String keyword, Instant searchedAt) {
}
