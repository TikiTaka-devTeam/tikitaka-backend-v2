package com.tikitaka.question.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tikitaka.document.entity.Document;
import com.tikitaka.user.entity.User;

class QuestionCategoryTests {

    @Test
    void manualCategoryKeepsDescriptionNull() {
        QuestionCategory category = QuestionCategory.createManual(
                mock(Document.class),
                "프로세스 상태",
                mock(User.class)
        );

        assertThat(category.getDescription()).isNull();
    }

    @Test
    void aiCategoryStoresAndUpdatesDescription() {
        QuestionCategory category = QuestionCategory.createByAi(
                mock(Document.class),
                "프로세스 상태",
                "Ready 상태",
                List.of(1)
        );

        category.updateAiMetadata(
                "Ready, Running, Waiting 상태와 상태 전이",
                List.of(1, 2)
        );

        assertThat(category.getDescription())
                .isEqualTo("Ready, Running, Waiting 상태와 상태 전이");
        assertThat(category.getSourcePages()).containsExactly(1, 2);
    }
}
