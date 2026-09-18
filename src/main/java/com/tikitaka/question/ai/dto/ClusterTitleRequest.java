package com.tikitaka.question.ai.dto;

public record ClusterTitleRequest(
        String title,
        String content,
        String categoryName
) {
}