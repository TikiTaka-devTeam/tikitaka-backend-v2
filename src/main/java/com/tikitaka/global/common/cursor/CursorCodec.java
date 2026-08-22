package com.tikitaka.global.common.cursor;

import java.util.Base64;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;

import tools.jackson.databind.ObjectMapper;

@Component
public class CursorCodec {
    private static final int MAX_ENCODED_LENGTH = 2048;

    private final ObjectMapper objectMapper;

    public CursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Object cursor) {
        Objects.requireNonNull(cursor, "cursor");
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encode cursor", exception);
        }
    }

    public <T> T decode(String encodedCursor, Class<T> cursorType) {
        if (encodedCursor == null || encodedCursor.isBlank()
                || encodedCursor.length() > MAX_ENCODED_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor);
            return objectMapper.readValue(json, cursorType);
        } catch (Exception exception) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR, exception);
        }
    }

    public <T> T decodeOrNull(String encodedCursor, Class<T> cursorType) {
        if (encodedCursor == null) return null;
        return decode(encodedCursor, cursorType);
    }
}
