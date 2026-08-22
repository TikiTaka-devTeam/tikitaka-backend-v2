package com.tikitaka.global.common.cursor;

import java.util.List;
import java.util.function.Function;

public record CursorResponse<T>(List<T> content, String nextCursor, boolean hasNext) {
    public CursorResponse { content = List.copyOf(content); }

    public static <T> CursorResponse<T> from(List<T> queryResult, int requestedSize,
                                             Function<T, String> cursorExtractor) {
        if (requestedSize <= 0) throw new IllegalArgumentException("requestedSize must be positive");
        boolean hasNext = queryResult.size() > requestedSize;
        List<T> content = List.copyOf(queryResult.subList(0, Math.min(queryResult.size(), requestedSize)));
        String nextCursor = hasNext && !content.isEmpty()
                ? cursorExtractor.apply(content.get(content.size() - 1)) : null;
        return new CursorResponse<>(content, nextCursor, hasNext);
    }
}
