package com.tikitaka.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Provider google, Provider kakao) {
    public record Provider(String clientId, String clientSecret, String redirectUri) {
    }
}
