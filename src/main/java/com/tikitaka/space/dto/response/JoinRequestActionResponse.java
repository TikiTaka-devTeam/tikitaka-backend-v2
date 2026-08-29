package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoinRequestActionResponse(

        @JsonProperty("approved_count")
        Integer approvedCount,

        @JsonProperty("denied_count")
        Integer deniedCount

) {

    public static JoinRequestActionResponse approved(int count) {
        return new JoinRequestActionResponse(
                count,
                null
        );
    }

    public static JoinRequestActionResponse denied(int count) {
        return new JoinRequestActionResponse(
                null,
                count
        );
    }
}