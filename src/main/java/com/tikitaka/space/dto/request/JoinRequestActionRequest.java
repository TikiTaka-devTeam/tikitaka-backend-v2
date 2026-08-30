package com.tikitaka.space.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record JoinRequestActionRequest(

        @JsonProperty("join_request_ids")
        @NotEmpty
        List<UUID> joinRequestIds

) {
}