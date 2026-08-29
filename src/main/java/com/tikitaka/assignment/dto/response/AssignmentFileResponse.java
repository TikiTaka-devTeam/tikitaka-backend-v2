package com.tikitaka.assignment.dto.response;

import java.util.UUID;

import com.tikitaka.assignment.entity.AssignmentFile;
import com.tikitaka.assignment.entity.SubmissionFile;

public record AssignmentFileResponse(
        UUID fileId,
        String fileName,
        String fileUrl
) {
    public static AssignmentFileResponse from(AssignmentFile file) {
        return new AssignmentFileResponse(file.getId(), file.getFileName(), file.getFileUrl());
    }

    public static AssignmentFileResponse from(SubmissionFile file) {
        return new AssignmentFileResponse(file.getId(), file.getFileName(), file.getFileUrl());
    }
}
