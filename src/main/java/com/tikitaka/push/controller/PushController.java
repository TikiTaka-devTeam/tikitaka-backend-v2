package com.tikitaka.push.controller;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.push.dto.request.PushEndpointRequest;
import com.tikitaka.push.dto.request.PushSubscriptionRequest;
import com.tikitaka.push.dto.response.PushDeleteResponse;
import com.tikitaka.push.dto.response.PushSubscriptionResponse;
import com.tikitaka.push.dto.response.VapidPublicKeyResponse;
import com.tikitaka.push.service.PushSubscriptionService;
import com.tikitaka.push.service.WebPushService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Push", description = "Web Push 구독 및 VAPID 키 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PushController {

    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushService webPushService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(
            summary = "PUSH-001 VAPID Public Key 조회",
            description = "브라우저에서 Web Push 구독 생성 시 사용하는 VAPID Public Key를 조회합니다."
    )
    @GetMapping("/vapid-public-key")
    public VapidPublicKeyResponse getVapidPublicKey() {
        return new VapidPublicKeyResponse(
                webPushService.getPublicKey()
        );
    }

    @Operation(
            summary = "PUSH-002 Push Subscription 등록",
            description = "현재 로그인 사용자의 브라우저 Web Push 구독 정보를 등록하거나 동일 endpoint 구독을 현재 사용자로 갱신합니다."
    )
    @PostMapping("/subscriptions")
    public PushSubscriptionResponse subscribe(
            @Valid @RequestBody PushSubscriptionRequest request,
            Authentication authentication
    ) {
        return pushSubscriptionService.subscribe(
                request,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(
            summary = "PUSH-003 Push Subscription 삭제",
            description = "현재 로그인 사용자의 특정 Web Push 구독 정보를 삭제합니다."
    )
    @DeleteMapping("/subscriptions/{subscriptionId}")
    public PushDeleteResponse unsubscribe(
            @PathVariable UUID subscriptionId,
            Authentication authentication
    ) {
        return pushSubscriptionService.unsubscribe(
                subscriptionId,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(
            summary = "PUSH-004 Push Subscription endpoint 기반 삭제",
            description = "현재 로그인 사용자의 endpoint 기준 Web Push 구독 정보를 삭제합니다."
    )
    @DeleteMapping("/subscriptions")
    public PushDeleteResponse unsubscribeByEndpoint(
            @Valid @RequestBody PushEndpointRequest request,
            Authentication authentication
    ) {
        return pushSubscriptionService.unsubscribeByEndpoint(
                request,
                currentUserResolver.resolve(authentication)
        );
    }
}
