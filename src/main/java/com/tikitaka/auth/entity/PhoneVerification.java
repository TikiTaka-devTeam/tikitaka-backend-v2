package com.tikitaka.auth.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tikitaka.global.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "phone_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_number", nullable = false, length = 11)
    private String phoneNumber;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "request_ip", nullable = false, columnDefinition = "inet")
    private String requestIp;

    @Column(name = "verification_code_hash", nullable = false, length = 64)
    private String verificationCodeHash;

    @Column(name = "code_expires_at", nullable = false)
    private Instant codeExpiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private PhoneVerificationDeliveryStatus deliveryStatus;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_token_hash", unique = true, length = 64)
    private String verificationTokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    private PhoneVerification(
            String phoneNumber,
            String requestIp,
            String verificationCodeHash,
            Instant codeExpiresAt,
            Instant resendAvailableAt
    ) {
        this.phoneNumber = phoneNumber;
        this.requestIp = requestIp;
        this.verificationCodeHash = verificationCodeHash;
        this.codeExpiresAt = codeExpiresAt;
        this.resendAvailableAt = resendAvailableAt;
        this.deliveryStatus = PhoneVerificationDeliveryStatus.PENDING;
    }

    public static PhoneVerification create(
            String phoneNumber,
            String requestIp,
            String verificationCodeHash,
            Instant codeExpiresAt,
            Instant resendAvailableAt
    ) {
        return new PhoneVerification(
                phoneNumber,
                requestIp,
                verificationCodeHash,
                codeExpiresAt,
                resendAvailableAt);
    }

    public void markSent(Instant sentAt) {
        this.deliveryStatus = PhoneVerificationDeliveryStatus.SENT;
        this.sentAt = sentAt;
        this.failedAt = null;
    }

    public void markFailed(Instant failedAt) {
        this.deliveryStatus = PhoneVerificationDeliveryStatus.FAILED;
        this.failedAt = failedAt;
    }

    public void recordFailedAttempt() {
        if (attemptCount < 5) {
            attemptCount++;
        }
    }

    public void verify(String tokenHash, Instant verifiedAt, Instant tokenExpiresAt) {
        this.verificationTokenHash = tokenHash;
        this.verifiedAt = verifiedAt;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public void consume(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public void invalidate(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public boolean isSent() {
        return deliveryStatus == PhoneVerificationDeliveryStatus.SENT;
    }

    public boolean isCodeExpired(Instant now) {
        return !codeExpiresAt.isAfter(now);
    }

    public boolean isResendLimited(Instant now) {
        return isSent() && resendAvailableAt.isAfter(now);
    }

    public boolean hasExceededAttempts() {
        return attemptCount >= 5;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }}


