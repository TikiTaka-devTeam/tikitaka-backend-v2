package com.tikitaka.assignment.exception;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum AssignmentErrorCode implements ErrorCode {
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "과제를 찾을 수 없습니다."),
    SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "Space를 찾을 수 없습니다."),
    SPACE_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "SPACE_MEMBER_REQUIRED", "해당 Space의 참여 멤버만 이용할 수 있습니다."),
    ASSIGNMENT_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "ASSIGNMENT_MANAGE_FORBIDDEN", "과제 관리 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AssignmentErrorCode(HttpStatus status, String code, String message) {
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
