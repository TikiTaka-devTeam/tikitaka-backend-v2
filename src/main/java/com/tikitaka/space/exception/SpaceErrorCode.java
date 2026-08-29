package com.tikitaka.space.exception;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum SpaceErrorCode implements ErrorCode {

    SPACE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SPACE_NOT_FOUND",
            "Space를 찾을 수 없습니다."
    ),

    SPACE_CODE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SPACE_CODE_NOT_FOUND",
            "유효하지 않은 Space 코드입니다."
    ),

    SPACE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "SPACE_ACCESS_DENIED",
            "해당 Space에 대한 권한이 없습니다."
    ),

    PROFESSOR_ONLY(
            HttpStatus.FORBIDDEN,
            "SPACE_PROFESSOR_ONLY",
            "교수 계정만 사용할 수 있습니다."
    ),

    STUDENT_ONLY(
            HttpStatus.FORBIDDEN,
            "SPACE_STUDENT_ONLY",
            "학생 계정만 사용할 수 있습니다."
    ),

    ALREADY_JOINED(
            HttpStatus.CONFLICT,
            "SPACE_ALREADY_JOINED",
            "이미 참여 중인 Space입니다."
    ),

    JOIN_REQUEST_ALREADY_PENDING(
            HttpStatus.CONFLICT,
            "SPACE_JOIN_REQUEST_ALREADY_PENDING",
            "이미 참여 승인 대기 중인 Space입니다."
    ),

    SPACE_ALREADY_ARCHIVED(
            HttpStatus.CONFLICT,
            "SPACE_ALREADY_ARCHIVED",
            "이미 보관된 Space입니다."
    ),

    SPACE_ALREADY_ACTIVE(
            HttpStatus.CONFLICT,
            "SPACE_ALREADY_ACTIVE",
            "이미 활성 상태인 Space입니다."
    ),

    ARCHIVED_SPACE_CANNOT_BE_MODIFIED(
            HttpStatus.CONFLICT,
            "SPACE_ARCHIVED_CANNOT_MODIFY",
            "보관된 Space는 수정할 수 없습니다."
    ),

    INVALID_SCHEDULE(
            HttpStatus.BAD_REQUEST,
            "SPACE_INVALID_SCHEDULE",
            "수업 시작 시간은 종료 시간보다 빨라야 합니다."
    ),

    INVALID_SPACE_NAME(
            HttpStatus.BAD_REQUEST,
            "SPACE_INVALID_NAME",
            "Space 이름은 비어 있을 수 없습니다."
    ),

    INVALID_SPACE_FILTER(
            HttpStatus.BAD_REQUEST,
            "SPACE_INVALID_FILTER",
            "Space 조회 조건이 올바르지 않습니다."
    ),

    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_UNAUTHENTICATED",
            "로그인이 필요합니다."
    ),

    USER_NOT_FOUND(
            HttpStatus.UNAUTHORIZED,
            "AUTH_USER_NOT_FOUND",
            "인증된 사용자를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    SpaceErrorCode(
            HttpStatus status,
            String code,
            String message
    ) {
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