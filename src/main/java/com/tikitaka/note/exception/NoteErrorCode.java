package com.tikitaka.note.exception;
import com.tikitaka.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
@Getter
@RequiredArgsConstructor
public enum NoteErrorCode implements ErrorCode {
    NOTE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "NOTE_ACCESS_DENIED", "필기에 접근할 권한이 없습니다."),
    NOTE_STROKE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTE_STROKE_NOT_FOUND", "필기를 찾을 수 없습니다."),
    NOTE_VERSION_CONFLICT(HttpStatus.CONFLICT, "NOTE_VERSION_CONFLICT", "필기 버전이 변경되었습니다. 최신 필기를 조회해 주세요."),
    NOTE_OPERATION_ID_CONFLICT(HttpStatus.CONFLICT, "NOTE_OPERATION_ID_CONFLICT", "동일 작업 ID에 다른 내용을 사용할 수 없습니다."),
    NOTE_CLIENT_STROKE_ID_CONFLICT(HttpStatus.CONFLICT, "NOTE_CLIENT_STROKE_ID_CONFLICT", "이미 사용한 클라이언트 필기 ID입니다."),
    FIXER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FIXER_ACCESS_DENIED", "수정 메모에 접근할 권한이 없습니다."),
    FIXER_NOT_FOUND(HttpStatus.NOT_FOUND, "FIXER_NOT_FOUND", "수정 메모를 찾을 수 없습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}