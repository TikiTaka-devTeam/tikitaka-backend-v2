package com.tikitaka.space.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceStatusResponse(

        @JsonProperty("space_id")
        UUID spaceId,

        @JsonProperty("status")
        @Schema(example = "ARCHIVED")
        String status
) {
}