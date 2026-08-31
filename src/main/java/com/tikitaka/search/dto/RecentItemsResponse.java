package com.tikitaka.search.dto;

import java.util.List;

public record RecentItemsResponse(
        List<RecentDocumentResponse> documents,
        List<RecentQuestionResponse> questions) {
}
