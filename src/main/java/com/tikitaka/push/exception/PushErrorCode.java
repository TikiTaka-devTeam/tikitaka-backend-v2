package com.tikitaka.push.exception;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum PushErrorCode implements ErrorCode {

    PUSH_SUBSCRIPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PUSH_SUBSCRIPTION_NOT_FOUND",
            "Push 구독 정보를 찾을 수 없습니다."
    ),

    WEB_PUSH_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "WEB_PUSH_NOT_CONFIGURED",
            "Web Push 설정이 완료되지 않았습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    PushErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
