package com.tikitaka.user;

import org.springframework.http.HttpStatus;
import com.tikitaka.global.exception.ErrorCode;

public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "USER_INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다."),
    PASSWORD_NOT_REGISTERED(HttpStatus.BAD_REQUEST, "USER_PASSWORD_NOT_REGISTERED", "비밀번호가 등록되지 않은 계정입니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "USER_SAME_PASSWORD", "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
    INVALID_PROFILE_IMAGE_REQUEST(HttpStatus.BAD_REQUEST, "USER_INVALID_PROFILE_IMAGE_REQUEST", "프로필 이미지 또는 삭제 요청 중 하나만 전달해야 합니다."),
    PROFILE_IMAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USER_PROFILE_IMAGE_UNAVAILABLE", "프로필 이미지 서비스를 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
