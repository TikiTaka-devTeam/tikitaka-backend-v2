package com.tikitaka.global.common.cursor;

public record CursorRequest(String cursor, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public CursorRequest {
        if (size <= 0) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
    }

    public static CursorRequest firstPage() { return new CursorRequest(null, DEFAULT_SIZE); }
    public int queryLimit() { return size + 1; }
}
