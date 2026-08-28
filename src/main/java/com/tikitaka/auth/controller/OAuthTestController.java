package com.tikitaka.auth.controller;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import com.tikitaka.auth.dto.OAuthLoginResponse;
import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.auth.oauth.OAuthErrorCode;
import com.tikitaka.auth.oauth.OAuthProperties;
import com.tikitaka.auth.oauth.OAuthStateService;
import com.tikitaka.auth.service.OAuthService;
import com.tikitaka.global.exception.BusinessException;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth/oauth")
@Tag(name = "사용자/인증 API", description = "사용자 계정, 인증 및 서비스 문의 API")
public class OAuthTestController {
    private final OAuthProperties properties;
    private final OAuthStateService stateService;
    private final OAuthService oauthService;

    public OAuthTestController(OAuthProperties properties, OAuthStateService stateService,
            OAuthService oauthService) {
        this.properties = properties;
        this.stateService = stateService;
        this.oauthService = oauthService;
    }

    @GetMapping("/google/test")
    @Operation(summary = "USR-001 Google 실제 로그인 테스트",
            description = "Swagger의 Execute가 아니라 [이 링크를 새 탭에서 열어 Google 로그인](http://localhost:8080/api/v1/auth/oauth/google/test)하세요.")
    public ResponseEntity<Void> googleLogin() {
        OAuthProperties.Provider config = requireConfig(properties.google());
        URI location = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("response_type", "code").queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri()).queryParam("scope", "openid email profile")
                .queryParam("state", stateService.issue(AuthProvider.GOOGLE)).build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/kakao/test")
    @Operation(summary = "USR-001 Kakao 실제 로그인 테스트",
            description = "Swagger의 Execute가 아니라 [이 링크를 새 탭에서 열어 Kakao 로그인](http://localhost:8080/api/v1/auth/oauth/kakao/test)하세요.")
    public ResponseEntity<Void> kakaoLogin() {
        OAuthProperties.Provider config = requireConfig(properties.kakao());
        URI location = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code").queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("state", stateService.issue(AuthProvider.KAKAO)).build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/{provider}/callback")
    @Operation(summary = "OAuth 테스트 콜백", description = "Google/Kakao가 호출하는 테스트용 콜백입니다. 직접 호출하지 않습니다.")
    public OAuthLoginResponse callback(@PathVariable String provider, @RequestParam String code,
            @RequestParam String state) {
        AuthProvider parsed = parseProvider(provider);
        try {
            stateService.validate(state, parsed);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED, exception);
        }
        return oauthService.authorize(provider, code);
    }

    private OAuthProperties.Provider requireConfig(OAuthProperties.Provider config) {
        if (config == null || config.clientId() == null || config.clientId().isBlank()
                || config.redirectUri() == null || config.redirectUri().isBlank()) {
            throw new BusinessException(OAuthErrorCode.AUTHENTICATION_FAILED);
        }
        return config;
    }

    private AuthProvider parseProvider(String provider) {
        try {
            return AuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(OAuthErrorCode.PROVIDER_UNSUPPORTED, exception);
        }
    }
}
