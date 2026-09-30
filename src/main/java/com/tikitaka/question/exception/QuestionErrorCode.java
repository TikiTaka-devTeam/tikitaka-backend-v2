package com.tikitaka.question.exception;

import org.springframework.http.HttpStatus;
import com.tikitaka.global.exception.ErrorCode;

public enum QuestionErrorCode implements ErrorCode {
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "질문을 찾을 수 없습니다."),
    ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "ANSWER_NOT_FOUND", "답변을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_CATEGORY_NOT_FOUND", "질문 카테고리를 찾을 수 없습니다."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_DOCUMENT_NOT_FOUND", "강의자료를 찾을 수 없습니다."),
    SLIDE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_SLIDE_NOT_FOUND", "슬라이드를 찾을 수 없습니다."),
    SPACE_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "QUESTION_SPACE_MEMBER_REQUIRED", "해당 강의의 참여자만 이용할 수 있습니다."),
    STUDENT_ONLY(HttpStatus.FORBIDDEN, "QUESTION_STUDENT_ONLY", "학생만 질문을 등록하거나 조회할 수 있습니다."),
    QUESTION_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "QUESTION_MANAGE_FORBIDDEN", "질문 관리 권한이 없습니다."),
    COMMENT_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "QUESTION_COMMENT_CREATE_FORBIDDEN", "해당 질문에 댓글을 작성할 권한이 없습니다."),
    AUTHOR_ONLY(HttpStatus.FORBIDDEN, "QUESTION_AUTHOR_ONLY", "작성자만 수정할 수 있습니다."),
    INVALID_SCOPE(HttpStatus.BAD_REQUEST, "QUESTION_INVALID_SCOPE", "SLIDE 범위에는 slide_id가 필요합니다."),
    INVALID_PIN(HttpStatus.BAD_REQUEST, "QUESTION_INVALID_PIN", "질문 위치는 0 이상 1 이하이어야 합니다."),
    INVALID_CATEGORY_OPERATION(HttpStatus.BAD_REQUEST, "QUESTION_INVALID_CATEGORY_OPERATION", "카테고리 작업 값이 올바르지 않습니다."),
    CATEGORY_DUPLICATED(HttpStatus.CONFLICT, "QUESTION_CATEGORY_DUPLICATED", "같은 이름의 카테고리가 이미 존재합니다."),
    LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT, "QUESTION_LIKE_ALREADY_EXISTS", "이미 공감한 질문입니다."),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_LIKE_NOT_FOUND", "취소할 공감이 없습니다.");

    private final HttpStatus status; private final String code; private final String message;
    QuestionErrorCode(HttpStatus status, String code, String message) { this.status=status; this.code=code; this.message=message; }
    public HttpStatus getStatus(){return status;} public String getCode(){return code;} public String getMessage(){return message;}
}
