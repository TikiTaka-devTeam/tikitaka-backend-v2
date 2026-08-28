package com.tikitaka.auth.oauth;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.global.exception.BusinessException;

@Component
public class RestOAuthProviderClient implements OAuthProviderClient {
    private final RestClient restClient;
    private final OAuthProperties properties;

    public RestOAuthProviderClient(RestClient.Builder builder, OAuthProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public OAuthProfile fetchProfile(AuthProvider provider, String authorizationCode) {
        try {
            return switch (provider) {
                case GOOGLE -> google(authorizationCode);
                case KAKAO -> kakao(authorizationCode);
            };
        } catch (RestClientException | ClassCastException exception) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private OAuthProfile google(String code) {
        OAuthProperties.Provider config = requireConfig(properties.google());
        Map<String, Object> token = token("https://oauth2.googleapis.com/token", code, config, true);
        Map<String, Object> profile = userInfo("https://openidconnect.googleapis.com/v1/userinfo", token);
        return profile(AuthProvider.GOOGLE, profile.get("sub"), profile.get("email"),
                profile.get("name"), profile.get("picture"));
    }

    @SuppressWarnings("unchecked")
    private OAuthProfile kakao(String code) {
        OAuthProperties.Provider config = requireConfig(properties.kakao());
        Map<String, Object> token = token("https://kauth.kakao.com/oauth/token", code, config, false);
        Map<String, Object> body = userInfo("https://kapi.kakao.com/v2/user/me", token);
        Map<String, Object> account = (Map<String, Object>) body.get("kakao_account");
        Map<String, Object> kakaoProfile = account == null ? null : (Map<String, Object>) account.get("profile");
        return profile(AuthProvider.KAKAO, body.get("id"), account == null ? null : account.get("email"),
                kakaoProfile == null ? null : kakaoProfile.get("nickname"),
                kakaoProfile == null ? null : kakaoProfile.get("profile_image_url"));
    }

    private Map<String, Object> token(String uri, String code, OAuthProperties.Provider config,
                                      boolean includeSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", config.clientId());
        form.add("redirect_uri", config.redirectUri());
        if (includeSecret || (config.clientSecret() != null && !config.clientSecret().isBlank())) {
            form.add("client_secret", config.clientSecret());
        }
        Map<String, Object> response = restClient.post().uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED);
        }
        return response;
    }

    private Map<String, Object> userInfo(String uri, Map<String, Object> token) {
        Map<String, Object> response = restClient.get().uri(uri)
                .headers(headers -> headers.setBearerAuth(token.get("access_token").toString()))
                .retrieve().body(Map.class);
        if (response == null) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED);
        }
        return response;
    }

    private OAuthProfile profile(AuthProvider provider, Object id, Object email, Object name, Object image) {
        if (id == null || email == null || name == null) {
            throw new BusinessException(OAuthErrorCode.PROFILE_INCOMPLETE);
        }
        return new OAuthProfile(provider, id.toString(), email.toString(), name.toString(),
                image == null ? null : image.toString());
    }

    private OAuthProperties.Provider requireConfig(OAuthProperties.Provider config) {
        if (config == null || config.clientId() == null || config.clientId().isBlank()
                || config.redirectUri() == null || config.redirectUri().isBlank()) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED);
        }
        return config;
    }
}
