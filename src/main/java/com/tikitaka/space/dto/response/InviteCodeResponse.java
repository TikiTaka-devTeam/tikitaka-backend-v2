package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record InviteCodeResponse(

        @JsonProperty("space_id")
        UUID spaceId,

        @JsonProperty("invite_code")
        String inviteCode,

        @JsonProperty("auto_approve")
        Boolean autoApprove

) {
}