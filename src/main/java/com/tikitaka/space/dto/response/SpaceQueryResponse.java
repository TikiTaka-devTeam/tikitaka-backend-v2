package com.tikitaka.space.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpaceQueryResponse(

        @JsonProperty("spaces")
        List<SpaceListResponse> spaces,

        @JsonProperty("pending_spaces")
        List<PendingSpaceResponse> pendingSpaces
) {
}