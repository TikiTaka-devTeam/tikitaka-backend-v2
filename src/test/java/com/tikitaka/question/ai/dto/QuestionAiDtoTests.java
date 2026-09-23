package com.tikitaka.question.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tikitaka.document.ai.dto.DocumentAnalyzeResponse;

import tools.jackson.databind.json.JsonMapper;

class QuestionAiDtoTests {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void documentAnalysisCategoryReadsDescription() {
        DocumentAnalyzeResponse response = mapper.readValue(
                """
                {"categories":[{
                  "name":"프로세스 상태",
                  "description":"Ready, Running, Waiting 상태와 상태 전이",
                  "source_pages":[1,2]
                }]}
                """,
                DocumentAnalyzeResponse.class
        );

        assertThat(response.categories()).singleElement().satisfies(category -> {
            assertThat(category.name()).isEqualTo("프로세스 상태");
            assertThat(category.description())
                    .isEqualTo("Ready, Running, Waiting 상태와 상태 전이");
        });
    }

    @Test
    void questionAnalysisCategorySendsDescription() {
        UUID categoryId = UUID.randomUUID();
        QuestionAnalyzeRequest request = new QuestionAnalyzeRequest(
                UUID.randomUUID(),
                "질문",
                "내용",
                null,
                null,
                List.of(new QuestionAnalyzeRequest.CategoryCandidate(
                        categoryId,
                        "프로세스 상태",
                        "Ready, Running, Waiting 상태와 상태 전이"
                ))
        );

        var category = mapper.readTree(mapper.writeValueAsString(request))
                .get("categories")
                .get(0);

        assertThat(category.get("category_id").asString()).isEqualTo(categoryId.toString());
        assertThat(category.get("name").asString()).isEqualTo("프로세스 상태");
        assertThat(category.get("description").asString())
                .isEqualTo("Ready, Running, Waiting 상태와 상태 전이");
    }
}
