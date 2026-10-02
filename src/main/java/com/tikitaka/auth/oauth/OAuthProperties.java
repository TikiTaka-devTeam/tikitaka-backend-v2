package com.tikitaka.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Provider google, Provider kakao) {
    public record Provider(String clientId, String clientSecret, String redirectUri,
                           List<String> allowedRedirectUris) {
        @ConstructorBinding
        public Provider {
            allowedRedirectUris = allowedRedirectUris == null ? List.of() : List.copyOf(allowedRedirectUris);
        }
        public Provider(String clientId, String clientSecret, String redirectUri) {
            this(clientId, clientSecret, redirectUri, List.of());
        }
    }
}
