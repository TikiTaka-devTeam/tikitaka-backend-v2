package com.tikitaka.space.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record JoinSettingsRequest(

        @JsonProperty("auto_approve")
        @NotNull
        Boolean autoApprove

) {
}