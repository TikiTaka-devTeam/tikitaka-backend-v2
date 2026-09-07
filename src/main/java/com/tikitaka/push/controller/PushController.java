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
import com.tikitaka.push.dto.request.PushSubscriptionRequest;
import com.tikitaka.push.dto.response.PushDeleteResponse;
import com.tikitaka.push.dto.response.PushSubscriptionResponse;
import com.tikitaka.push.dto.response.VapidPublicKeyResponse;
import com.tikitaka.push.service.PushSubscriptionService;
import com.tikitaka.push.service.WebPushService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PushController {

    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushService webPushService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/vapid-public-key")
    public VapidPublicKeyResponse getVapidPublicKey() {
        return new VapidPublicKeyResponse(
                webPushService.getPublicKey()
        );
    }

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
}
