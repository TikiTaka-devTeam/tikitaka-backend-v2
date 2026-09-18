package com.tikitaka.document.ai.dto;

import java.util.List;

public record DocumentCategoryResult(
        String name,
        List<Integer> sourcePages
) {
}