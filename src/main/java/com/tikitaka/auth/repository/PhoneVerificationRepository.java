package com.tikitaka.auth.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.auth.entity.PhoneVerification;
import com.tikitaka.auth.entity.PhoneVerificationDeliveryStatus;

import jakarta.persistence.LockModeType;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerification> findFirstByPhoneNumberAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            String phoneNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select verification from PhoneVerification verification "
            + "where verification.verificationTokenHash = :tokenHash")
    Optional<PhoneVerification> findForUpdateByVerificationTokenHash(
            @Param("tokenHash") String verificationTokenHash);

    long countByPhoneNumberAndDeliveryStatusInAndCreatedAtGreaterThanEqual(
            String phoneNumber,
            Collection<PhoneVerificationDeliveryStatus> deliveryStatuses,
            Instant createdAt);

    long countByRequestIpAndDeliveryStatusInAndCreatedAtGreaterThanEqual(
            String requestIp,
            Collection<PhoneVerificationDeliveryStatus> deliveryStatuses,
            Instant createdAt);
}


