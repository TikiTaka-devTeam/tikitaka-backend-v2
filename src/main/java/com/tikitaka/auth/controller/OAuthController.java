package com.tikitaka.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.tikitaka.auth.dto.OAuthAuthorizationRequest;
import com.tikitaka.auth.dto.OAuthLoginResponse;
import com.tikitaka.auth.dto.OAuthSignupRequest;
import com.tikitaka.auth.dto.OAuthSignupResponse;
import com.tikitaka.auth.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth/oauth")
@Tag(name = "사용자/인증 API", description = "사용자 계정, 인증 및 서비스 문의 API")
public class OAuthController {
    private final OAuthService oauthService;

    public OAuthController(OAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @PostMapping("/{provider}")
    @Operation(summary = "USR-001 OAuth 인증 및 로그인")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "로그인 또는 추가 가입 정보 반환"),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 제공자 또는 불완전한 프로필"),
            @ApiResponse(responseCode = "401", description = "OAuth 인증 실패")})
    public OAuthLoginResponse authorize(@PathVariable String provider,
            @RequestBody @Valid OAuthAuthorizationRequest request) {
        return oauthService.authorize(provider, request.authorizationCode(), request.redirectUri());
    }

    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "USR-002 소셜 회원가입")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "회원가입 및 로그인 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 또는 휴대폰 인증 오류"),
            @ApiResponse(responseCode = "409", description = "이메일, 휴대폰 또는 OAuth 계정 중복")})
    public OAuthSignupResponse signup(
            @RequestPart("signup_data") @Valid OAuthSignupRequest request,
            @RequestPart(value = "profile_image", required = false) MultipartFile profileImage) {
        return oauthService.signup(request, profileImage);
    }
}
