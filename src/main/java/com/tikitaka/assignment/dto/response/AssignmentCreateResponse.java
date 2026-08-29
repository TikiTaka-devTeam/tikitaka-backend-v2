package com.tikitaka.assignment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssignmentCreateResponse(
        UUID assignmentId,
        String title,
        String description,
        Instant dueAt,
        String closeType,
        String status,
        List<AssignmentFileResponse> files
) {
}
