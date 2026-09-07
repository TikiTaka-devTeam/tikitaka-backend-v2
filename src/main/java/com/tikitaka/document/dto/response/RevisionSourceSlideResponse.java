package com.tikitaka.document.dto.response;
import java.util.UUID;
public record RevisionSourceSlideResponse(UUID revisionSlideId, Integer sourcePageNumber, String thumbnailUrl) {}
