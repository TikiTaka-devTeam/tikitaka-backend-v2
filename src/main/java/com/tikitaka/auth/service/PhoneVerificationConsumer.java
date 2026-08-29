package com.tikitaka.auth.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tikitaka.auth.PhoneNumberNormalizer;
import com.tikitaka.auth.PhoneVerificationHasher;
import com.tikitaka.auth.entity.PhoneVerification;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.repository.PhoneVerificationRepository;
import com.tikitaka.global.exception.BusinessException;

@Service
public class PhoneVerificationConsumer {
    private final PhoneVerificationRepository repository;
    private final PhoneVerificationHasher hasher;
    private final PhoneNumberNormalizer normalizer;
    private final Clock clock;

    public PhoneVerificationConsumer(PhoneVerificationRepository repository, PhoneVerificationHasher hasher,
            PhoneNumberNormalizer normalizer, Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.normalizer = normalizer;
        this.clock = clock;
    }

    @Transactional
    public void consume(String token, String expectedPhoneNumber) {
        PhoneVerification verification = repository.findForUpdateByVerificationTokenHash(hasher.hashToken(token))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PHONE_VERIFICATION_TOKEN_INVALID));
        Instant now = clock.instant();
        if (verification.getConsumedAt() != null) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_TOKEN_CONSUMED);
        }
        if (verification.getTokenExpiresAt() == null || !verification.getTokenExpiresAt().isAfter(now)
                || verification.getInvalidatedAt() != null) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_TOKEN_INVALID);
        }
        if (!verification.getPhoneNumber().equals(normalizer.normalize(expectedPhoneNumber))) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_PHONE_MISMATCH);
        }
        verification.consume(now);
    }
}
