package com.tikitaka.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AssignmentListItemResponse(
        UUID assignmentId,
        String title,
        String contentPreview,
        Instant dueAt,
        String status,
        String submissionStatus
) {
}
