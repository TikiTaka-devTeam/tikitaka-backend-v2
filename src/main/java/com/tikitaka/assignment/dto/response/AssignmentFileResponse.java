package com.tikitaka.assignment.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.assignment.entity.AssignmentFile;
import com.tikitaka.assignment.entity.SubmissionFile;

public record AssignmentFileResponse(

        @JsonProperty("file_id")
        UUID fileId,

        @JsonProperty("file_name")
        String fileName,

        @JsonProperty("file_url")
        String fileUrl
) {
    public static AssignmentFileResponse from(AssignmentFile file) {
        return new AssignmentFileResponse(
                file.getId(),
                file.getFileName(),
                file.getFileUrl()
        );
    }

    public static AssignmentFileResponse from(SubmissionFile file) {
        return new AssignmentFileResponse(
                file.getId(),
                file.getFileName(),
                file.getFileUrl()
        );
    }
}