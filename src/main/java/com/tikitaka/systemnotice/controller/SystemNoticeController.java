package com.tikitaka.systemnotice.controller;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.systemnotice.dto.response.*;
import com.tikitaka.systemnotice.service.SystemNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "System Notice",
        description = "시스템 공지사항 조회 API"
)
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name=OpenApiConfig.BEARER_AUTH)

public class SystemNoticeController {
    private final SystemNoticeService systemNoticeService;
    private final CurrentUserResolver currentUserResolver;
    
    @Operation(summary="SYS-NOT-001 시스템 공지사항 목록 조회")
    @GetMapping("/api/v1/system-notices")
    
    public SystemNoticeListResponse getSystemNotices(
        @RequestParam(required=false) String cursor,
        @RequestParam(defaultValue="20") int size,
        Authentication authentication){
            return systemNoticeService.getSystemNotices(cursor,size,currentUserResolver.resolve(authentication));
    }
    @Operation(summary="SYS-NOT-002 시스템 공지사항 상세 조회 및 읽음 처리")
    @GetMapping("/api/v1/system-notices/{systemNoticeId}")
    public SystemNoticeDetailResponse getSystemNotice(
        @PathVariable UUID systemNoticeId,
        Authentication authentication){
            return systemNoticeService.getSystemNotice(systemNoticeId,currentUserResolver.resolve(authentication));
    }
}
