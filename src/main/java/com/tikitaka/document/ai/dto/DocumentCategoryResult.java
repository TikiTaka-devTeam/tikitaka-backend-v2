package com.tikitaka.document.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DocumentCategoryResult(

        String name,

        @JsonProperty("source_pages")
        List<Integer> sourcePages
) {
}