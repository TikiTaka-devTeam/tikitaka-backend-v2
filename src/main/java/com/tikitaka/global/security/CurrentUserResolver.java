package com.tikitaka.global.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.space.exception.SpaceErrorCode;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public User resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(SpaceErrorCode.UNAUTHENTICATED);
        }

        String principalName = authentication.getName();
        if (principalName == null || principalName.isBlank()) {
            throw new BusinessException(SpaceErrorCode.UNAUTHENTICATED);
        }

        try {
            UUID userId = UUID.fromString(principalName);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(SpaceErrorCode.USER_NOT_FOUND));
        } catch (IllegalArgumentException ignored) {
            return userRepository.findByEmail(principalName)
                    .orElseThrow(() -> new BusinessException(SpaceErrorCode.USER_NOT_FOUND));
        }
    }
}
