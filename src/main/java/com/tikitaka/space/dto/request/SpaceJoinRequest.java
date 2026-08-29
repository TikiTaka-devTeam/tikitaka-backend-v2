package com.tikitaka.space.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SpaceJoinRequest(

        @JsonProperty("space_code")
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{8}$")
        @Schema(example = "A1B2C3D4")
        String spaceCode
) {
}