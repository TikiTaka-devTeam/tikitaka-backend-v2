package com.tikitaka.auth.exception;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_PHONE_ALREADY_EXISTS", "이미 사용 중인 전화번호입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다."),
    INVALID_PHONE_VERIFICATION(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PHONE_VERIFICATION", "휴대폰 인증 정보가 올바르지 않습니다."),
    PHONE_NUMBER_ALREADY_REGISTERED(HttpStatus.CONFLICT, "PHONE_NUMBER_ALREADY_REGISTERED", "이미 가입된 휴대폰 번호입니다."),
    PHONE_VERIFICATION_RESEND_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "PHONE_VERIFICATION_RESEND_LIMITED", "인증번호를 다시 요청하기 전에 잠시 기다려주세요."),
    PHONE_VERIFICATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "PHONE_VERIFICATION_RATE_LIMITED", "인증번호 발송 요청 한도를 초과했습니다."),
    PHONE_VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_CODE_MISMATCH", "인증번호가 일치하지 않습니다."),
    PHONE_VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_CODE_EXPIRED", "인증번호가 만료되었습니다."),
    PHONE_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "PHONE_VERIFICATION_ATTEMPTS_EXCEEDED", "인증번호 확인 가능 횟수를 초과했습니다."),
    PHONE_VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_TOKEN_INVALID", "휴대폰 인증 토큰이 올바르지 않거나 만료되었습니다."),
    PHONE_VERIFICATION_TOKEN_CONSUMED(HttpStatus.CONFLICT, "PHONE_VERIFICATION_TOKEN_CONSUMED", "이미 사용된 휴대폰 인증 토큰입니다."),
    PHONE_VERIFICATION_PHONE_MISMATCH(HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_PHONE_MISMATCH", "인증된 휴대폰 번호와 가입 요청 번호가 일치하지 않습니다."),
    PROFILE_UPLOAD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROFILE_UPLOAD_UNAVAILABLE", "프로필 이미지를 업로드할 수 없습니다."),
    SMS_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SMS_DELIVERY_UNAVAILABLE", "인증번호 문자를 발송할 수 없습니다.");

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

