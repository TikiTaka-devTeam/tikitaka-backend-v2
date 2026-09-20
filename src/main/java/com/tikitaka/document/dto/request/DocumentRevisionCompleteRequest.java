package com.tikitaka.document.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentRevisionCompleteRequest(
        @JsonProperty("base_preview_version") Integer basePreviewVersion,
        String title
) {}
