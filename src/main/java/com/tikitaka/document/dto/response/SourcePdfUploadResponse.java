package com.tikitaka.document.dto.response;
import java.util.List;
import java.util.UUID;
public record SourcePdfUploadResponse(UUID revisionId, String sourceFileName, String sourcePdfUrl, Integer sourcePageCount, List<RevisionSourceSlideResponse> revisionSlides) {}
