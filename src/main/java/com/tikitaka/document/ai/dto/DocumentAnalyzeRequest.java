package com.tikitaka.document.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record DocumentAnalyzeRequest(

        @JsonProperty("document_id")
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