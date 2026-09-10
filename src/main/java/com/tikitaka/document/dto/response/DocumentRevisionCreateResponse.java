package com.tikitaka.document.dto.response;
import java.util.UUID;
import com.tikitaka.document.entity.DocumentRevision;
import com.tikitaka.document.entity.RevisionStatus;
public record DocumentRevisionCreateResponse(UUID revisionId, UUID documentId, Integer baseDocumentVersion, Integer previewVersion, RevisionStatus status) {
    public static DocumentRevisionCreateResponse from(DocumentRevision r) { return new DocumentRevisionCreateResponse(r.getId(), r.getDocument().getId(), r.getBaseDocumentVersion(), r.getPreviewVersion(), r.getStatus()); }
}
