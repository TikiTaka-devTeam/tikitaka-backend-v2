package com.tikitaka.auth;

import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.service.PhoneVerificationService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tikitaka.auth.dto.PhoneVerificationConfirmResponse;
import com.tikitaka.auth.entity.PhoneVerification;
import com.tikitaka.auth.entity.PhoneVerificationDeliveryStatus;
import com.tikitaka.auth.repository.PhoneVerificationRepository;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.sms.SmsSender;
import com.tikitaka.user.repository.UserRepository;

class PhoneVerificationServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
    private static final String PHONE = "01012345678";
    private static final String IP = "127.0.0.1";
    private static final String CODE = "123456";

    private final PhoneVerificationRepository verificationRepository = mock(PhoneVerificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SmsSender smsSender = mock(SmsSender.class);
    private final PhoneVerificationCodeGenerator codeGenerator = mock(PhoneVerificationCodeGenerator.class);
    private final PhoneVerificationTokenGenerator tokenGenerator = mock(PhoneVerificationTokenGenerator.class);
    private final PhoneVerificationHasher hasher = new PhoneVerificationHasher("test-pepper");
    private PhoneVerificationService service;

    @BeforeEach
    void setUp() {
        service = new PhoneVerificationService(verificationRepository, userRepository, smsSender,
                new PhoneNumberNormalizer(), codeGenerator, tokenGenerator, hasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsCodeToNormalizedPhoneAndStoresOnlyHash() {
        when(codeGenerator.generate()).thenReturn(CODE);

        service.sendCode("010-1234-5678", IP);

        verify(smsSender).send(PHONE, "[Tikitaka] 인증번호는 123456입니다. 3분 내 입력해주세요.");
        ArgumentCaptor<PhoneVerification> captor = ArgumentCaptor.forClass(PhoneVerification.class);
        verify(verificationRepository).save(captor.capture());
        PhoneVerification saved = captor.getValue();
        assertThat(saved.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(saved.getVerificationCodeHash()).isNotEqualTo(CODE);
        assertThat(saved.getCodeExpiresAt()).isEqualTo(NOW.plusSeconds(180));
        assertThat(saved.getResendAvailableAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(saved.getDeliveryStatus()).isEqualTo(PhoneVerificationDeliveryStatus.SENT);
    }

    @Test
    void rejectsRegisteredPhone() {
        when(userRepository.existsByPhoneNumber(PHONE)).thenReturn(true);

        assertError(() -> service.sendCode(PHONE, IP), AuthErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
    }

    @Test
    void rejectsResendWithinSixtySeconds() {
        PhoneVerification latest = sentVerification(NOW.plusSeconds(180), NOW.plusSeconds(60));
        when(verificationRepository.findFirstByPhoneNumberAndInvalidatedAtIsNullOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(latest));

        assertError(() -> service.sendCode(PHONE, IP), AuthErrorCode.PHONE_VERIFICATION_RESEND_LIMITED);
    }

    @Test
    void confirmsCodeAndIssuesTenMinuteOneTimeToken() {
        PhoneVerification verification = sentVerification(NOW.plusSeconds(180), NOW);
        when(verificationRepository.findFirstByPhoneNumberAndInvalidatedAtIsNullOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(verification));
        when(tokenGenerator.generate()).thenReturn("opaque-token");

        PhoneVerificationConfirmResponse response = service.confirmCode("010-1234-5678", CODE);

        assertThat(response.verified()).isTrue();
        assertThat(response.verificationToken()).isEqualTo("opaque-token");
        assertThat(response.expiresIn()).isEqualTo(600);
        assertThat(verification.getVerificationTokenHash()).isEqualTo(hasher.hashToken("opaque-token"));
        assertThat(verification.getTokenExpiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void recordsMismatchAndBlocksFifthFailure() {
        PhoneVerification verification = sentVerification(NOW.plusSeconds(180), NOW);
        when(verificationRepository.findFirstByPhoneNumberAndInvalidatedAtIsNullOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(verification));

        for (int attempt = 1; attempt < 5; attempt++) {
            assertError(() -> service.confirmCode(PHONE, "000000"),
                    AuthErrorCode.PHONE_VERIFICATION_CODE_MISMATCH);
        }
        assertError(() -> service.confirmCode(PHONE, "000000"),
                AuthErrorCode.PHONE_VERIFICATION_ATTEMPTS_EXCEEDED);
        assertThat(verification.getAttemptCount()).isEqualTo(5);
    }

    @Test
    void rejectsExpiredCode() {
        PhoneVerification verification = sentVerification(NOW, NOW);
        when(verificationRepository.findFirstByPhoneNumberAndInvalidatedAtIsNullOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(verification));

        assertError(() -> service.confirmCode(PHONE, CODE),
                AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED);
    }

    private PhoneVerification sentVerification(Instant expiresAt, Instant resendAt) {
        PhoneVerification verification = PhoneVerification.create(
                PHONE, IP, hasher.hashCode(PHONE, CODE), expiresAt, resendAt);
        verification.markSent(NOW.minusSeconds(1));
        return verification;
    }

    private void assertError(Runnable action, AuthErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}