package com.tikitaka.document.dto.response;
import java.util.UUID;
import com.tikitaka.document.entity.RevisionStatus;
public record DocumentRevisionCancelResponse(UUID revisionId, RevisionStatus status) {}
