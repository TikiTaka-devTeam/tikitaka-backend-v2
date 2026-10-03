package com.tikitaka.space.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

class SpaceAutoArchiveServiceTests {
    @ParameterizedTest
    @CsvSource({
            "2026-06-30T14:59:59Z, 2026, false",
            "2026-06-30T15:00:00Z, 2026, true",
            "2026-12-31T14:59:59Z, 2026, true",
            "2026-12-31T15:00:00Z, 2027, false",
            "2027-02-15T00:00:00Z, 2027, false"
    })
    void usesKoreanSemesterBoundaryEvenWithUtcClock(String time, int year, boolean firstSemesterEnded) {
        Instant now = Instant.parse(time);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Timestamp timestamp = Timestamp.from(now);
        when(jdbc.update(anyString(), eq(timestamp), eq(timestamp), eq(year), eq(year), eq(firstSemesterEnded)))
                .thenReturn(2);
        SpaceAutoArchiveService service = new SpaceAutoArchiveService(jdbc, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.archiveExpiredSpaces()).isEqualTo(2);
        verify(jdbc).update(anyString(), eq(timestamp), eq(timestamp), eq(year), eq(year), eq(firstSemesterEnded));
    }
}
