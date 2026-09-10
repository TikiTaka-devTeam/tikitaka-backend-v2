package com.tikitaka.document.dto.response;
import java.util.List;
import java.util.UUID;
public record DocumentSlidesResponse(UUID documentId, String pdfUrl, Integer pageCount, List<DocumentSlideResponse> slides) {}
