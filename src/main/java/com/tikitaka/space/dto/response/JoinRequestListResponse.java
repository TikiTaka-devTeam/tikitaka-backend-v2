package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JoinRequestListResponse(

        @JsonProperty("join_requests")
        List<JoinRequestResponse> joinRequests

) {
}