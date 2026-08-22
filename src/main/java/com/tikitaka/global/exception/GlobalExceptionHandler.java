package com.tikitaka.global.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleArgumentTypeMismatch() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindingException(BindException exception) {
        List<ErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<ErrorResponse.FieldError> errors = exception.getConstraintViolations().stream()
                .map(error -> new ErrorResponse.FieldError(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(CommonErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestPart() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType() {
        return ResponseEntity.status(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptableMediaType() {
        return ResponseEntity.status(CommonErrorCode.NOT_ACCEPTABLE.getStatus())
                .build();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded() {
        return ResponseEntity.status(CommonErrorCode.FILE_SIZE_EXCEEDED.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.FILE_SIZE_EXCEEDED));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMethod() {
        return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound() {
        return ResponseEntity.status(CommonErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}
