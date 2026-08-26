package com.tikitaka.auth;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_PHONE_ALREADY_EXISTS", "이미 사용 중인 전화번호입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다."),
    INVALID_PHONE_VERIFICATION(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PHONE_VERIFICATION", "휴대폰 인증 정보가 올바르지 않습니다."),
    PROFILE_UPLOAD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROFILE_UPLOAD_UNAVAILABLE", "프로필 이미지를 업로드할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
