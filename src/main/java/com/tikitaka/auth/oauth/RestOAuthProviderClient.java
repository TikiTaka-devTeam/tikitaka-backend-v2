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
import com.tikitaka.global.exception.CommonErrorCode;

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
        return fetchProfile(provider, authorizationCode, null);
    }

    @Override
    public OAuthProfile fetchProfile(AuthProvider provider, String authorizationCode, String redirectUri) {
        try {
            return switch (provider) {
                case GOOGLE -> google(authorizationCode, redirectUri);
                case KAKAO -> kakao(authorizationCode, redirectUri);
            };
        } catch (RestClientException | ClassCastException exception) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private OAuthProfile google(String code, String redirectUri) {
        OAuthProperties.Provider config = requireConfig(properties.google());
        Map<String, Object> token = token("https://oauth2.googleapis.com/token", code, config, true, redirectUri);
        Map<String, Object> profile = userInfo("https://openidconnect.googleapis.com/v1/userinfo", token);
        return profile(AuthProvider.GOOGLE, profile.get("sub"), profile.get("email"),
                profile.get("name"), profile.get("picture"));
    }

    @SuppressWarnings("unchecked")
    private OAuthProfile kakao(String code, String redirectUri) {
        OAuthProperties.Provider config = requireConfig(properties.kakao());
        Map<String, Object> token = token("https://kauth.kakao.com/oauth/token", code, config, false, redirectUri);
        Map<String, Object> body = userInfo("https://kapi.kakao.com/v2/user/me", token);
        Map<String, Object> account = (Map<String, Object>) body.get("kakao_account");
        Map<String, Object> kakaoProfile = account == null ? null : (Map<String, Object>) account.get("profile");
        return profile(AuthProvider.KAKAO, body.get("id"), account == null ? null : account.get("email"),
                kakaoProfile == null ? null : kakaoProfile.get("nickname"),
                kakaoProfileImage(kakaoProfile));
    }

    static Object kakaoProfileImage(Map<String, Object> kakaoProfile) {
        if (kakaoProfile == null || Boolean.TRUE.equals(kakaoProfile.get("is_default_image"))) {
            return null;
        }
        return kakaoProfile.get("profile_image_url");
    }

    private Map<String, Object> token(String uri, String code, OAuthProperties.Provider config,
                                      boolean includeSecret, String requestedRedirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", config.clientId());
        form.add("redirect_uri", resolveRedirectUri(config, requestedRedirectUri));
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

    private String resolveRedirectUri(OAuthProperties.Provider config, String requested) {
        if (requested == null) return config.redirectUri();
        if (requested.equals(config.redirectUri())
                || (config.allowedRedirectUris() != null && config.allowedRedirectUris().contains(requested))) {
            return requested;
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
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
        if (id == null || name == null || name.toString().isBlank()) {
            throw new BusinessException(OAuthErrorCode.PROFILE_INCOMPLETE);
        }
        return new OAuthProfile(provider, id.toString(), email == null ? null : email.toString(),
                name.toString(), image == null ? null : image.toString());
    }

    private OAuthProperties.Provider requireConfig(OAuthProperties.Provider config) {
        if (config == null || config.clientId() == null || config.clientId().isBlank()
                || config.redirectUri() == null || config.redirectUri().isBlank()) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED);
        }
        return config;
    }
}
