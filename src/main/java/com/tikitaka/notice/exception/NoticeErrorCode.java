package com.tikitaka.notice.exception;

import org.springframework.http.HttpStatus;
import com.tikitaka.global.exception.ErrorCode;

public enum NoticeErrorCode implements ErrorCode {
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다."),
    NOTICE_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "NOTICE_MANAGE_FORBIDDEN", "공지 관리 권한이 없습니다."),
    INVALID_RETAINED_FILE(HttpStatus.BAD_REQUEST, "NOTICE_INVALID_RETAINED_FILE", "유지할 첨부파일 정보가 올바르지 않습니다.");

    private final HttpStatus status; private final String code; private final String message;
    NoticeErrorCode(HttpStatus status, String code, String message) { this.status=status; this.code=code; this.message=message; }
    public HttpStatus getStatus(){ return status; } public String getCode(){ return code; } public String getMessage(){ return message; }
}
