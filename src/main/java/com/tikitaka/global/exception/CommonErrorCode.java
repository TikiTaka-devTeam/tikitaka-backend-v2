package com.tikitaka.global.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_INVALID_INPUT", "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_INVALID_REQUEST", "요청을 읽을 수 없습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "COMMON_INVALID_CURSOR", "커서 값이 올바르지 않습니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "COMMON_NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "업로드할 파일이 비어 있습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "FILE_INVALID_TYPE", "허용하지 않는 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "FILE_SIZE_EXCEEDED", "허용된 파일 크기를 초과했습니다."),
    FILE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_COUNT_EXCEEDED", "첨부할 수 있는 파일 개수를 초과했습니다."),
    REQUEST_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "FILE_REQUEST_SIZE_EXCEEDED", "첨부파일 전체 크기를 초과했습니다."),
    S3_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3_UPLOAD_FAILED", "파일 업로드에 실패했습니다."),
    S3_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3_DELETE_FAILED", "파일 삭제에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CommonErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
