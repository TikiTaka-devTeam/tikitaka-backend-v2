package com.tikitaka.push.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.push.dto.request.PushSubscriptionRequest;
import com.tikitaka.push.dto.response.PushDeleteResponse;
import com.tikitaka.push.dto.response.PushSubscriptionResponse;
import com.tikitaka.push.entity.PushSubscription;
import com.tikitaka.push.exception.PushErrorCode;
import com.tikitaka.push.repository.PushSubscriptionRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Transactional
    public PushSubscriptionResponse subscribe(
            PushSubscriptionRequest request,
            User currentUser
    ) {
        PushSubscription subscription = pushSubscriptionRepository
                .findByEndpoint(request.endpoint())
                .map(existing -> updateExisting(existing, request, currentUser))
                .orElseGet(() -> pushSubscriptionRepository.save(
                        PushSubscription.create(
                                currentUser,
                                request.endpoint(),
                                request.keys().p256dh(),
                                request.keys().auth()
                        )
                ));

        return new PushSubscriptionResponse(
                subscription.getId(),
                subscription.getCreatedAt()
        );
    }

    @Transactional
    public PushDeleteResponse unsubscribe(
            UUID subscriptionId,
            User currentUser
    ) {
        PushSubscription subscription = pushSubscriptionRepository
                .findByIdAndUserId(subscriptionId, currentUser.getId())
                .orElseThrow(() -> new BusinessException(
                        PushErrorCode.PUSH_SUBSCRIPTION_NOT_FOUND
                ));

        pushSubscriptionRepository.delete(subscription);

        return new PushDeleteResponse("Push 구독이 해제되었습니다.");
    }

    private PushSubscription updateExisting(
            PushSubscription existing,
            PushSubscriptionRequest request,
            User currentUser
    ) {
        if (!existing.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException(
                    PushErrorCode.PUSH_ENDPOINT_ALREADY_REGISTERED
            );
        }

        existing.updateKeys(
                request.keys().p256dh(),
                request.keys().auth()
        );

        return existing;
    }
}
