package com.tikitaka.question.ai.dto;

import java.util.List;
import java.util.UUID;

import com.tikitaka.question.entity.QuestionScope;

public record QuestionAnalyzeResponse(
        QuestionScope relation,
        List<QuestionCategoryResult> categories,
        UUID primaryCategoryId,
        float[] embedding
) {

    public boolean isCourseRelated() {
        return relation == QuestionScope.COURSE_RELATED;
    }

    public boolean isOther() {
        return relation == QuestionScope.OTHER;
    }
}