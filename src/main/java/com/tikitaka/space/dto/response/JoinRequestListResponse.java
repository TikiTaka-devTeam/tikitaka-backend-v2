package com.tikitaka.space.dto.response;

import java.util.List;

public record JoinRequestListResponse(
        List<JoinRequestResponse> joinRequests
) {
}
