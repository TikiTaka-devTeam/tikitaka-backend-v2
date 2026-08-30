package com.tikitaka.space.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MemberListResponse(

        @JsonProperty("total_count")
        Integer totalCount,

        @JsonProperty("members")
        List<MemberListItemResponse> members

) {
}