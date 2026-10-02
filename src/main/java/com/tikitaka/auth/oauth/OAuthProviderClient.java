package com.tikitaka.auth.oauth;

import com.tikitaka.auth.entity.AuthProvider;

public interface OAuthProviderClient {
    OAuthProfile fetchProfile(AuthProvider provider, String authorizationCode);
    OAuthProfile fetchProfile(AuthProvider provider, String authorizationCode, String redirectUri);
}
