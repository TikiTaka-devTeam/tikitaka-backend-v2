package com.tikitaka.document.dto.response;
import java.util.List;
import java.util.UUID;
import com.tikitaka.document.entity.RevisionStatus;
public record DocumentRevisionDetailResponse(UUID revisionId, UUID documentId, RevisionStatus status, Integer baseDocumentVersion, Integer previewVersion, String title, List<RevisionPageResponse> previewPages, boolean canUndo, boolean canRedo) {}
