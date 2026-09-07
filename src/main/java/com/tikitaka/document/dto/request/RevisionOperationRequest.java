package com.tikitaka.document.dto.request;
import java.util.List;
import java.util.UUID;
import com.tikitaka.document.entity.RevisionOperationType;
public record RevisionOperationRequest(UUID clientOperationId, Integer basePreviewVersion, RevisionOperationType type, List<UUID> revisionSlideIds, Integer position, List<UUID> pageIds) {}
