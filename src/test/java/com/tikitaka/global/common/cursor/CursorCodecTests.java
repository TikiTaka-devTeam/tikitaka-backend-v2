package com.tikitaka.global.common.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class CursorCodecTests {
    private final CursorCodec codec = new CursorCodec(JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build());

    @Test
    void roundTripsDomainSpecificCursor() {
        CreatedAtCursor original = new CreatedAtCursor(
                Instant.parse("2026-08-21T03:00:00Z"), UUID.randomUUID());

        String encoded = codec.encode(original);
        CreatedAtCursor decoded = codec.decode(encoded, CreatedAtCursor.class);

        assertThat(decoded).isEqualTo(original);
        assertThat(encoded).doesNotContain(original.id().toString());
    }

    @Test
    void returnsNullOnlyWhenCursorIsOmitted() {
        assertThat(codec.decodeOrNull(null, CreatedAtCursor.class)).isNull();
    }

    @Test
    void rejectsBlankCursor() {
        assertInvalidCursor(" ");
    }

    @Test
    void rejectsMalformedBase64Cursor() {
        assertInvalidCursor("not-a-valid-cursor!");
    }

    @Test
    void rejectsCursorWithWrongPayload() {
        assertInvalidCursor(codec.encode(new DifferentCursor("not-an-instant", "not-a-uuid")));
    }

    private void assertInvalidCursor(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor, CreatedAtCursor.class))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CURSOR);
    }

    record CreatedAtCursor(Instant createdAt, UUID id) {
    }

    record DifferentCursor(String createdAt, String id) {
    }
}
