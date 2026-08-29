package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record JoinSettingsResponse(

        @JsonProperty("space_id")
        UUID spaceId,

        @JsonProperty("auto_approve")
        Boolean autoApprove

) {
}