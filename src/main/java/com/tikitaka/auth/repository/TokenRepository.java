package com.tikitaka.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.auth.entity.Token;

public interface TokenRepository extends JpaRepository<Token, UUID> {

    Optional<Token> findByRefreshTokenHash(String refreshTokenHash);

    List<Token> findAllByUserId(UUID userId);

    List<Token> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId,
            Instant now
    );

    void deleteAllByUserId(UUID userId);
}