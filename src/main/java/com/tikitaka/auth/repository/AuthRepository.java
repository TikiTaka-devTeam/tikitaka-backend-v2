package com.tikitaka.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.auth.entity.Auth;
import com.tikitaka.auth.entity.AuthProvider;

public interface AuthRepository extends JpaRepository<Auth, UUID> {

    Optional<Auth> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    List<Auth> findAllByUserId(UUID userId);

    boolean existsByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );
}