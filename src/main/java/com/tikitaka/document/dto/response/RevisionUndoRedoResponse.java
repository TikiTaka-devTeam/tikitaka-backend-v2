package com.tikitaka.document.dto.response;
import java.util.UUID;
public record RevisionUndoRedoResponse(UUID revisionId, UUID operationId, Integer previewVersion, boolean canUndo, boolean canRedo) {}
