package com.tikitaka.search.dto;

import java.util.List;

public record SearchResponse(
        List<SearchDocumentResponse> documents,
        List<SearchAnnouncementResponse> announcements,
        List<SearchQuestionResponse> questions) {
}
