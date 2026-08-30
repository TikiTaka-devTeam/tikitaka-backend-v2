package com.tikitaka.global.security;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public User resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        return userRepository.findById(authenticatedUser.userId())
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.UNAUTHORIZED)
                );
    }
}