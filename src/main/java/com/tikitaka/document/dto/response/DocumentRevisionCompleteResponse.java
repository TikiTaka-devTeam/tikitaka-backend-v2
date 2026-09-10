package com.tikitaka.document.dto.response;

import java.util.UUID;

import com.tikitaka.document.entity.DocumentRevision;
import com.tikitaka.document.entity.RevisionStatus;

public record DocumentRevisionCompleteResponse(
        UUID revisionId,
        UUID documentId,
        RevisionStatus status
) {
    public static DocumentRevisionCompleteResponse processing(DocumentRevision revision) {
        return new DocumentRevisionCompleteResponse(
                revision.getId(),
                revision.getDocument().getId(),
                revision.getStatus());
    }
}
