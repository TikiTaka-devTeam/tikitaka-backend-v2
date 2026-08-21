package com.tikitaka.global.common.cursor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CursorResponseTests {
    @Test
    void createsNextCursorWhenAnExtraItemExists() {
        CursorResponse<Integer> response = CursorResponse.from(List.of(5, 4, 3), 2, String::valueOf);

        assertThat(response.content()).containsExactly(5, 4);
        assertThat(response.nextCursor()).isEqualTo("4");
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void omitsNextCursorOnLastPage() {
        CursorResponse<Integer> response = CursorResponse.from(List.of(2, 1), 2, String::valueOf);

        assertThat(response.content()).containsExactly(2, 1);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }
}
