package com.tikitaka.search.dto.response;

import java.util.List;

public record RecentItemsResponse(
        List<RecentDocumentResponse> documents,
        List<RecentQuestionResponse> questions) {
}
