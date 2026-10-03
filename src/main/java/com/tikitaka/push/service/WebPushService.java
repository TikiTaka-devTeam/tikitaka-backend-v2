package com.tikitaka.push.service;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.push.dto.response.WebPushPayload;
import com.tikitaka.push.entity.PushSubscription;
import com.tikitaka.push.exception.PushErrorCode;
import com.tikitaka.push.repository.PushSubscriptionRepository;

import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class WebPushService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushService(
            PushSubscriptionRepository pushSubscriptionRepository,
            ObjectMapper objectMapper,
            @Value("${webpush.vapid.public-key:}") String publicKey,
            @Value("${webpush.vapid.private-key:}") String privateKey,
            @Value("${webpush.vapid.subject:}") String subject
    ) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.objectMapper = objectMapper;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    public String getPublicKey() {
        if (publicKey.isBlank()) {
            throw new BusinessException(PushErrorCode.WEB_PUSH_NOT_CONFIGURED);
        }
        return publicKey;
    }

    public void sendToUser(UUID userId, WebPushPayload payload) {
        if (!isConfigured()) {
            log.warn("Web Push skipped because VAPID is not configured.");
            return;
        }

        List<PushSubscription> subscriptions =
                pushSubscriptionRepository.findAllByUserId(userId);

        if (subscriptions.isEmpty()) {
            return;
        }

        String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            log.warn("Web Push payload serialization failed. userId={}", userId);
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            send(subscription, serializedPayload);
        }
    }

    private void send(
            PushSubscription subscription,
            String payload
    ) {
        try {
            PushService pushService = new PushService(
                    publicKey,
                    privateKey,
                    subject
            );

            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();

            if (status == 404 || status == 410) {
                pushSubscriptionRepository.delete(subscription);
                log.warn(
                        "Web Push subscription removed after provider response. status={}, subscriptionId={}",
                        status,
                        subscription.getId()
                );
                return;
            }

            if (status < 200 || status >= 300) {
                String responseBody = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                responseBody = responseBody.replaceAll("\\s+", " ").trim();
                if (responseBody.length() > 512) {
                    responseBody = responseBody.substring(0, 512);
                }
                log.warn(
                        "Web Push delivery failed. status={}, subscriptionId={}, providerResponse={}",
                        status,
                        subscription.getId(),
                        responseBody
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Web Push delivery interrupted. subscriptionId={}",
                    subscription.getId()
            );
        } catch (Exception exception) {
            log.warn(
                    "Web Push delivery failed. subscriptionId={}, reason={}",
                    subscription.getId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private boolean isConfigured() {
        return !publicKey.isBlank()
                && !privateKey.isBlank()
                && !subject.isBlank();
    }
}
