package com.tikitaka.space.exception;

import com.tikitaka.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SpaceMemberErrorCode implements ErrorCode {

    SPACE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_SPACE_NOT_FOUND",
            "Space를 찾을 수 없습니다."
    ),
    SPACE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "MEMBER_SPACE_ACCESS_DENIED",
            "해당 Space에 참여 중인 멤버가 아닙니다."
    ),
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_NOT_FOUND",
            "멤버를 찾을 수 없습니다."
    ),
    JOIN_REQUEST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "JOIN_REQUEST_NOT_FOUND",
            "가입 요청을 찾을 수 없습니다."
    ),
    INVALID_JOIN_REQUEST(
            HttpStatus.BAD_REQUEST,
            "INVALID_JOIN_REQUEST",
            "처리할 수 없는 가입 요청이 포함되어 있습니다."
    ),
    MEMBER_MANAGE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "MEMBER_MANAGE_FORBIDDEN",
            "멤버 관리 권한이 없습니다."
    ),
    PROFESSOR_ONLY(
            HttpStatus.FORBIDDEN,
            "MEMBER_PROFESSOR_ONLY",
            "교수만 변경할 수 있습니다."
    ),
    CANNOT_REMOVE_SELF(
            HttpStatus.BAD_REQUEST,
            "MEMBER_CANNOT_REMOVE_SELF",
            "본인은 내보낼 수 없습니다."
    ),
    CANNOT_REMOVE_PROFESSOR(
            HttpStatus.BAD_REQUEST,
            "MEMBER_CANNOT_REMOVE_PROFESSOR",
            "교수는 내보낼 수 없습니다."
    ),
    PROFESSOR_ROLE_CHANGE_FORBIDDEN(
            HttpStatus.BAD_REQUEST,
            "MEMBER_PROFESSOR_ROLE_CHANGE_FORBIDDEN",
            "교수 역할은 변경할 수 없습니다."
    ),
    STUDENT_PERMISSION_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_STUDENT_PERMISSION_NOT_ALLOWED",
            "학생에게는 조교 권한을 설정할 수 없습니다."
    ),
    PERMISSION_VIEW_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "MEMBER_PERMISSION_VIEW_FORBIDDEN",
            "조교 권한을 조회할 권한이 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    SpaceMemberErrorCode(
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