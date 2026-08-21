package com.tikitaka.global.exception;

import java.util.List;

public record ErrorResponse(String code, String message, List<FieldError> errors) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.copyOf(errors));
    }

    public record FieldError(String field, String reason) {
    }
}
