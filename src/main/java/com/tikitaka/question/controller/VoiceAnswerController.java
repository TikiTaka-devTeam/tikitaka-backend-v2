package com.tikitaka.question.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.question.dto.response.VoiceAnswerResponse;
import com.tikitaka.question.service.VoiceAnswerService;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Question",
        description = "질문 및 공식 답변 API"
)
@RestController
@RequiredArgsConstructor
@SecurityRequirement(
        name = OpenApiConfig.BEARER_AUTH
)
public class VoiceAnswerController {

    private final VoiceAnswerService voiceAnswerService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(
            summary = "QST-021 음성 공식 답변 등록",
            description = """
                    교수 또는 QUESTION_MANAGE 권한을 가진 조교가
                    음성 파일을 이용해 공식 답변을 등록합니다.

                    원본 음성을 저장한 뒤 AI STT 및 발화 정규화를 수행하고,
                    정규화된 내용을 공식 답변으로 저장합니다.
                    """
    )
    @PostMapping(
            value = "/api/v1/questions/{questionId}/answers/voice",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<VoiceAnswerResponse> createVoiceAnswer(
            @PathVariable UUID questionId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        VoiceAnswerResponse response =
                voiceAnswerService
                        .createVoiceAnswer(
                                questionId,
                                file,
                                currentUser
                        );

        return ResponseEntity.ok(
                response
        );
    }
}