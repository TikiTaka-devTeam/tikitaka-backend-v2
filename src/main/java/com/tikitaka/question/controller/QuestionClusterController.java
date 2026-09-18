package com.tikitaka.question.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.question.dto.response.QuestionClusterResponse;
import com.tikitaka.question.service.QuestionClusterQueryService;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Question",
        description = "질문 및 AI 질문 클러스터 API"
)
@RestController
@RequiredArgsConstructor
@SecurityRequirement(
        name = OpenApiConfig.BEARER_AUTH
)
public class QuestionClusterController {

    private final QuestionClusterQueryService
            questionClusterQueryService;

    private final CurrentUserResolver
            currentUserResolver;

    @Operation(
            summary = "QST-022 질문 클러스터 조회",
            description = """
                    강의자료의 질문을
                    Category → Cluster → Question 구조로 조회합니다.

                    PDF 분석으로 생성된 Category를 기준으로
                    AI가 그룹화한 질문 Cluster를 반환합니다.
                    """
    )
    @GetMapping(
            "/api/v1/documents/{documentId}/question-clusters"
    )
    public ResponseEntity<QuestionClusterResponse> getQuestionClusters(
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        QuestionClusterResponse response =
                questionClusterQueryService
                        .getClusters(
                                documentId,
                                currentUser
                        );

        return ResponseEntity.ok(
                response
        );
    }
}