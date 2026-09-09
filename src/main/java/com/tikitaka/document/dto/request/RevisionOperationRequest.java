package com.tikitaka.document.dto.request;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.document.entity.RevisionOperationType;
public record RevisionOperationRequest(
        @JsonProperty("client_operation_id") UUID clientOperationId,
        @JsonProperty("base_preview_version") Integer basePreviewVersion,
        RevisionOperationType type,
        @JsonProperty("revision_slide_ids") List<UUID> revisionSlideIds,
        Integer position,
        @JsonProperty("page_ids") List<UUID> pageIds
) {}
