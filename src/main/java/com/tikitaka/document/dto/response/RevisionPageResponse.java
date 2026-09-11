package com.tikitaka.document.dto.response;
import java.util.UUID;
import com.tikitaka.document.entity.RevisionPageStatus;
import com.tikitaka.document.entity.RevisionSourceType;
public record RevisionPageResponse(UUID pageId, Integer position, RevisionSourceType sourceType, RevisionPageStatus status, String thumbnailUrl) {}
