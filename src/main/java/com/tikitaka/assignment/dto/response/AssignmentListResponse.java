package com.tikitaka.assignment.dto.response;

import java.util.List;

public record AssignmentListResponse(
        List<AssignmentListItemResponse> assignments
) {
}
