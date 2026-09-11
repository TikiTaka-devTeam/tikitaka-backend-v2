package com.tikitaka.document.dto.response;
import java.util.UUID;
public record RevisionOperationResponse(UUID operationId, Integer sequence, Integer previewVersion, boolean canUndo, boolean canRedo) {}
