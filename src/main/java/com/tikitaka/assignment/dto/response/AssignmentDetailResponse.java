package com.tikitaka.assignment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssignmentDetailResponse(
        UUID assignmentId,
        String title,
        String description,
        Instant createdAt,
        String professorName,
        Integer viewCount,
        List<AssignmentFileResponse> files,
        Instant dueAt,
        String status,
        AssignmentSubmissionResponse mySubmission,
        String gradingStatus,
        BigDecimal score,
        BigDecimal maxScore
) {
}
