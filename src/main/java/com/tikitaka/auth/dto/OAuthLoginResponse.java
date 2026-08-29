package com.tikitaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tikitaka.global.security.TokenPair;
import com.tikitaka.user.entity.User;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthLoginResponse(
        boolean signupRequired,
        String accessToken,
        String refreshToken,
        LoginResponse.UserSummary user,
        String signupToken,
        OAuthProfileResponse oauthProfile
) {
    public static OAuthLoginResponse login(TokenPair tokens, User user) {
        return new OAuthLoginResponse(false, tokens.accessToken(), tokens.refreshToken(),
                new LoginResponse.UserSummary(user.getId(), user.getName(), user.getAccountType()),
                null, null);
    }

    public static OAuthLoginResponse signup(String signupToken, OAuthProfileResponse profile) {
        return new OAuthLoginResponse(true, null, null, null, signupToken, profile);
    }
}
