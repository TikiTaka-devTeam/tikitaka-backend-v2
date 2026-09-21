package com.tikitaka.document.ai.dto;

import java.util.List;

public record DocumentAnalyzeResponse(
        List<DocumentCategoryResult> categories
) {
}