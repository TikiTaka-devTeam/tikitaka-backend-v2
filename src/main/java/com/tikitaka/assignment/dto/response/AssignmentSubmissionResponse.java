package com.tikitaka.assignment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssignmentSubmissionResponse(
        UUID submissionId,
        String comment,
        List<AssignmentFileResponse> files,
        String status,
        Instant submittedAt
) {
}
