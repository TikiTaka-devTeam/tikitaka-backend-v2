package com.tikitaka.inquiry;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tikitaka.global.security.AuthenticatedUser;
import com.tikitaka.inquiry.dto.InquiryCreateRequest;
import com.tikitaka.inquiry.dto.InquiryCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inquiries")
@Tag(name = "사용자/인증 API", description = "사용자 계정, 인증 및 서비스 문의 API")
@SecurityRequirement(name = "bearerAuth")
public class InquiryController {
    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @PostMapping
    @Operation(summary = "USR-014 서비스 문의 작성", description = "로그인 사용자가 계정 및 이용 문의, 오류 신고, 기능 제안 등의 서비스 문의를 등록합니다. "
            + "로그인 사용자만 호출할 수 있으며 Bearer 액세스 토큰 인증이 필요합니다. "
            + "요청 본문에 문의 유형 type, 제목 title, 내용 content를 전달합니다. "
            + "등록이 완료되면 문의 ID, 문의 유형, 접수 상태 및 생성 시각을 반환합니다. type은 ACCOUNT_USAGE, ERROR_REPORT, SUGGESTION_OTHER 중 하나여야 합니다.")
    public InquiryCreateResponse create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid InquiryCreateRequest request
    ) {
        return inquiryService.create(authenticatedUser.userId(), request);
    }
}
