package com.tikitaka.document.ai.dto;

import java.util.List;
import java.util.UUID;

public record DocumentAnalyzeRequest(
        UUID documentId,
        String title,
        List<PageContent> pages
) {

    public record PageContent(
            int page,
            String text
    ) {
    }
}