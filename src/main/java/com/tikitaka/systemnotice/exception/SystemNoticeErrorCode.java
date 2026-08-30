package com.tikitaka.systemnotice.exception;

import org.springframework.http.HttpStatus;
import com.tikitaka.global.exception.ErrorCode;

public enum SystemNoticeErrorCode implements ErrorCode {
    SYSTEM_NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND,"SYSTEM_NOTICE_NOT_FOUND",
    "시스템 공지사항을 찾을 수 없습니다.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
    
    SystemNoticeErrorCode(HttpStatus status,String code,String message){
        this.status=status;this.code=code;this.message=message;
    }

    public HttpStatus getStatus(){
        return status;
    }

    public String getCode(){
        return code;
    }
    
    public String getMessage(){
        return message;
    }
}
