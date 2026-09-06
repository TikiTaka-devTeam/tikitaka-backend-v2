package com.tikitaka.assignment.exception;

import org.springframework.http.HttpStatus;

import com.tikitaka.global.exception.ErrorCode;

public enum AssignmentErrorCode implements ErrorCode {

    ASSIGNMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ASSIGNMENT_NOT_FOUND",
            "과제를 찾을 수 없습니다."
    ),

    ASSIGNMENT_FILE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ASSIGNMENT_FILE_NOT_FOUND",
            "과제 첨부파일을 찾을 수 없습니다."
    ),

    SPACE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SPACE_NOT_FOUND",
            "Space를 찾을 수 없습니다."
    ),

    SPACE_MEMBER_REQUIRED(
            HttpStatus.FORBIDDEN,
            "SPACE_MEMBER_REQUIRED",
            "해당 Space의 참여 멤버만 이용할 수 있습니다."
    ),

    ASSIGNMENT_MANAGE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "ASSIGNMENT_MANAGE_FORBIDDEN",
            "과제 관리 권한이 없습니다."
    ),

    STUDENT_ONLY(
            HttpStatus.FORBIDDEN,
            "ASSIGNMENT_STUDENT_ONLY",
            "학생만 과제를 제출할 수 있습니다."
    ),

    PROFESSOR_ONLY(
            HttpStatus.FORBIDDEN,
            "ASSIGNMENT_PROFESSOR_ONLY",
            "교수만 처리할 수 있습니다."
    ),

    ASSIGNMENT_CLOSED(
            HttpStatus.BAD_REQUEST,
            "ASSIGNMENT_CLOSED",
            "마감된 과제는 제출하거나 수정할 수 없습니다."
    ),

    SUBMISSION_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "SUBMISSION_ALREADY_EXISTS",
            "이미 제출한 과제입니다."
    ),

    SUBMISSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SUBMISSION_NOT_FOUND",
            "제출한 과제를 찾을 수 없습니다."
    ),

    STUDENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ASSIGNMENT_STUDENT_NOT_FOUND",
            "해당 과제의 학생을 찾을 수 없습니다."
    ),

    SCORE_EXCEEDS_MAX_SCORE(
            HttpStatus.BAD_REQUEST,
            "ASSIGNMENT_SCORE_EXCEEDS_MAX_SCORE",
            "점수는 과제 만점을 초과할 수 없습니다."
    ),

    GRADING_ALREADY_FINALIZED(
            HttpStatus.BAD_REQUEST,
            "ASSIGNMENT_GRADING_ALREADY_FINALIZED",
            "이미 성적이 최종 등록된 과제입니다."
    ),

    GRADING_NOT_FINALIZED(
            HttpStatus.BAD_REQUEST,
            "ASSIGNMENT_GRADING_NOT_FINALIZED",
            "아직 성적이 최종 등록되지 않았습니다."
    ),

    UNGRADED_STUDENT_EXISTS(
            HttpStatus.BAD_REQUEST,
            "ASSIGNMENT_UNGRADED_STUDENT_EXISTS",
            "점수가 입력되지 않은 학생이 있습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    AssignmentErrorCode(
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