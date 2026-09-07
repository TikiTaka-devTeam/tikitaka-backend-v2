package com.tikitaka.notification.exception;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "NOTIFICATION_NOT_FOUND",
            "알림을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    NotificationErrorCode(HttpStatus status, String code, String message) {
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
