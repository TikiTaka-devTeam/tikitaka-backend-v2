package com.tikitaka.auth.dto;

import com.tikitaka.global.security.TokenPair;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;

import java.util.UUID;

public record OAuthSignupResponse(
        String accessToken,
        String refreshToken,
        UserDetail user
) {
    public static OAuthSignupResponse of(TokenPair tokens, User user) {
        return of(tokens, user, user.getProfileUrl());
    }

    public static OAuthSignupResponse of(TokenPair tokens, User user, String profileUrl) {
        return new OAuthSignupResponse(tokens.accessToken(), tokens.refreshToken(), UserDetail.from(user, profileUrl));
    }

    public record UserDetail(UUID userId, String email, String name, String phoneNumber,
                             AccountType accountType, String univ, String major,
                             String memberIdNumber, String profileUrl) {
        static UserDetail from(User user, String profileUrl) {
            return new UserDetail(user.getId(), user.getEmail(), user.getName(), user.getPhoneNumber(),
                    user.getAccountType(), user.getUniv(), user.getMajor(),
                    user.getMemberIdNumber(), profileUrl);
        }
    }
}
