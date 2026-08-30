package com.tikitaka.auth.oauth;

import com.tikitaka.auth.entity.AuthProvider;

public record OAuthProfile(AuthProvider provider, String providerUserId, String email,
                           String name, String profileUrl) {
}
