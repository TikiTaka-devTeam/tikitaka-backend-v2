package com.tikitaka.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.PhoneNumberNormalizer;
import com.tikitaka.auth.PhoneVerificationCodeGenerator;
import com.tikitaka.auth.PhoneVerificationHasher;
import com.tikitaka.auth.PhoneVerificationTokenGenerator;
import com.tikitaka.auth.dto.PhoneVerificationConfirmResponse;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.entity.PhoneVerification;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.entity.PhoneVerificationDeliveryStatus;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.repository.PhoneVerificationRepository;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.sms.SmsSender;
import com.tikitaka.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class PhoneVerificationService {
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final List<PhoneVerificationDeliveryStatus> COUNTED_DELIVERIES = List.of(
            PhoneVerificationDeliveryStatus.PENDING, PhoneVerificationDeliveryStatus.SENT);

    private final PhoneVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final SmsSender smsSender;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final PhoneVerificationCodeGenerator codeGenerator;
    private final PhoneVerificationTokenGenerator tokenGenerator;
    private final PhoneVerificationHasher hasher;
    private final Clock clock;

    public PhoneVerificationService(PhoneVerificationRepository verificationRepository,
            UserRepository userRepository, SmsSender smsSender,
            PhoneNumberNormalizer phoneNumberNormalizer,
            PhoneVerificationCodeGenerator codeGenerator,
            PhoneVerificationTokenGenerator tokenGenerator,
            PhoneVerificationHasher hasher, Clock clock) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.smsSender = smsSender;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.codeGenerator = codeGenerator;
        this.tokenGenerator = tokenGenerator;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public void sendCode(String phoneNumber, String requestIp) {
        String normalizedPhone = phoneNumberNormalizer.normalize(phoneNumber);
        Instant now = clock.instant();
        if (userRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new BusinessException(AuthErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        }
        verificationRepository.findFirstByPhoneNumberAndInvalidatedAtIsNullOrderByCreatedAtDesc(normalizedPhone)
                .filter(verification -> verification.isResendLimited(now))
                .ifPresent(verification -> {
                    throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_RESEND_LIMITED);
                });
        ensureRateLimit(normalizedPhone, requestIp, now);

        String code = codeGenerator.generate();
        PhoneVerification verification = verificationRepository.save(PhoneVerification.create(
                normalizedPhone, requestIp, hasher.hashCode(normalizedPhone, code),
                now.plus(Duration.ofMinutes(3)), now.plus(Duration.ofSeconds(60))));
        try {
            smsSender.send(normalizedPhone,
                    "[Tikitaka] 인증번호는 " + code + "입니다. 3분 내 입력해주세요.");
            verification.markSent(clock.instant());
        } catch (BusinessException exception) {
            verification.markFailed(clock.instant());
            throw exception;
        }
    }

    @Transactional
    public PhoneVerificationConfirmResponse confirmCode(String phoneNumber, String verificationCode) {
        String normalizedPhone = phoneNumberNormalizer.normalize(phoneNumber);
        Instant now = clock.instant();
        PhoneVerification verification = verificationRepository
                .findFirstByPhoneNumberAndInvalidatedAtIsNullOrderByCreatedAtDesc(normalizedPhone)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_MISMATCH));
        if (!verification.isSent()) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_MISMATCH);
        }
        if (verification.hasExceededAttempts()) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (verification.isCodeExpired(now)) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED);
        }
        if (verification.isVerified()) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_TOKEN_INVALID);
        }
        if (!hasher.matchesCode(normalizedPhone, verificationCode, verification.getVerificationCodeHash())) {
            verification.recordFailedAttempt();
            if (verification.hasExceededAttempts()) {
                throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_ATTEMPTS_EXCEEDED);
            }
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_MISMATCH);
        }

        String token = tokenGenerator.generate();
        verification.verify(hasher.hashToken(token), now, now.plus(TOKEN_TTL));
        return new PhoneVerificationConfirmResponse(true, token, TOKEN_TTL.toSeconds());
    }

    private void ensureRateLimit(String phoneNumber, String requestIp, Instant now) {
        if (phoneCount(phoneNumber, now.minus(ONE_HOUR)) >= 5
                || phoneCount(phoneNumber, now.minus(ONE_DAY)) >= 10
                || ipCount(requestIp, now.minus(ONE_HOUR)) >= 20
                || ipCount(requestIp, now.minus(ONE_DAY)) >= 50) {
            throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
        }
    }

    private long phoneCount(String phoneNumber, Instant since) {
        return verificationRepository.countByPhoneNumberAndDeliveryStatusInAndCreatedAtGreaterThanEqual(
                phoneNumber, COUNTED_DELIVERIES, since);
    }

    private long ipCount(String requestIp, Instant since) {
        return verificationRepository.countByRequestIpAndDeliveryStatusInAndCreatedAtGreaterThanEqual(
                requestIp, COUNTED_DELIVERIES, since);
    }
}
