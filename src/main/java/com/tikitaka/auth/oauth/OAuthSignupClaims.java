package com.tikitaka.auth.oauth;

import com.tikitaka.auth.entity.AuthProvider;

public record OAuthSignupClaims(AuthProvider provider, String providerUserId, String email,
                                String name, String profileUrl) {
}
