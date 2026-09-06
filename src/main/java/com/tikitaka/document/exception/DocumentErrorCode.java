package com.tikitaka.document.exception;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum DocumentErrorCode implements ErrorCode {
    INVALID_DOCUMENT_TITLE(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_TITLE", "강의자료 제목이 올바르지 않습니다."),
    INVALID_PDF_FILE(HttpStatus.BAD_REQUEST, "INVALID_PDF_FILE", "올바른 PDF 파일이 아닙니다."),
    ENCRYPTED_PDF_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "ENCRYPTED_PDF_NOT_SUPPORTED", "암호화된 PDF는 등록할 수 없습니다."),
    PDF_PAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "PDF_PAGE_LIMIT_EXCEEDED", "PDF 최대 페이지 수를 초과했습니다."),
    DOCUMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_DENIED", "강의자료에 접근할 권한이 없습니다."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "강의자료를 찾을 수 없습니다."),
    PDF_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PDF_PROCESSING_FAILED", "PDF 처리에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    DocumentErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
