package com.tikitaka.space.dto.response;

import java.util.List;

public record MemberListResponse(
        int totalCount,
        List<MemberListItemResponse> members
) {
}
