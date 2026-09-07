package com.tikitaka.push.controller;

import com.tikitaka.auth.service.CustomUserDetails;
import com.tikitaka.push.dto.request.PushSubscriptionRequest;
import com.tikitaka.push.dto.response.PushDeleteResponse;
import com.tikitaka.push.dto.response.PushSubscriptionResponse;
import com.tikitaka.push.dto.response.VapidPublicKeyResponse;
import com.tikitaka.push.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Push", description = "Web Push 구독 및 VAPID 키 API")
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final PushSubscriptionService pushSubscriptionService;

    @Operation(
            summary = "VAPID Public Key 조회",
            description = "브라우저에서 Web Push 구독을 생성할 때 사용하는 VAPID Public Key를 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "VAPID Public Key 조회 성공")
    @GetMapping("/vapid-public-key")
    public VapidPublicKeyResponse getVapidPublicKey() {
        return pushSubscriptionService.getVapidPublicKey();
    }

    @Operation(
            summary = "Push Subscription 등록",
            description = "현재 로그인 사용자의 브라우저 Web Push 구독 정보를 등록합니다. "
                    + "동일 endpoint가 이미 존재하는 경우 기존 구독 정보를 갱신합니다."
    )
    @ApiResponse(responseCode = "200", description = "Push Subscription 등록 성공")
    @PostMapping("/subscriptions")
    public PushSubscriptionResponse subscribe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        return pushSubscriptionService.subscribe(
                userDetails.getUserId(),
                request
        );
    }

    @Operation(
            summary = "Push Subscription 삭제",
            description = "현재 로그인 사용자의 특정 Web Push 구독 정보를 삭제합니다."
    )
    @ApiResponse(responseCode = "200", description = "Push Subscription 삭제 성공")
    @DeleteMapping("/subscriptions/{subscriptionId}")
    public PushDeleteResponse unsubscribe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID subscriptionId
    ) {
        return pushSubscriptionService.unsubscribe(
                userDetails.getUserId(),
                subscriptionId
        );
    }
}