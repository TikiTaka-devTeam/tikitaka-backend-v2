package com.tikitaka.auth.oauth;

import org.springframework.http.HttpStatus;
import com.tikitaka.global.exception.ErrorCode;

public enum OAuthErrorCode implements ErrorCode {
    PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "OAUTH_PROVIDER_UNSUPPORTED", "지원하지 않는 OAuth 제공자입니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "OAUTH_AUTHENTICATION_FAILED", "OAuth 인증에 실패했습니다."),
    PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "OAUTH_PROFILE_INCOMPLETE", "OAuth 제공자에서 필수 회원 정보를 받을 수 없습니다."),
    SIGNUP_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "OAUTH_SIGNUP_TOKEN_INVALID", "OAuth 회원가입 토큰이 올바르지 않거나 만료되었습니다."),
    ACCOUNT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "OAUTH_ACCOUNT_ALREADY_REGISTERED", "이미 가입된 OAuth 계정입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    OAuthErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
