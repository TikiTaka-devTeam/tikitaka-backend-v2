package com.tikitaka.question.dto.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.question.entity.QuestionStatus;

public record QuestionClusterResponse(

        @JsonProperty("document_id")
        UUID documentId,

        List<CategoryItem> categories
) {

    public record CategoryItem(

            @JsonProperty("category_id")
            UUID categoryId,

            String name,

            List<ClusterItem> clusters
    ) {
    }

    public record ClusterItem(

            @JsonProperty("cluster_id")
            UUID clusterId,

            @JsonProperty("summary_title")
            String summaryTitle,

            @JsonProperty("member_count")
            int memberCount,

            List<QuestionItem> questions
    ) {
    }

    public record QuestionItem(

            @JsonProperty("question_id")
            UUID questionId,

            String title,

            String content,

            QuestionStatus status,

            @JsonProperty("like_count")
            int likeCount,

            Double similarity,

            @JsonProperty("is_representative")
            boolean representative
    ) {
    }
}