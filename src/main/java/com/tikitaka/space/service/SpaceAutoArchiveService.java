package com.tikitaka.space.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceAutoArchiveService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SpaceAutoArchiveService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public int archiveExpiredSpaces() {
        Instant now = clock.instant();
        ZonedDateTime nowKst = now.atZone(KST);
        int year = nowKst.getYear();
        boolean firstSemesterEnded = nowKst.getMonthValue() >= 7;

        // Updating only active rows makes repeated and concurrent runs harmless.
        return jdbcTemplate.update("""
                UPDATE spaces
                SET active_status = FALSE, archived_at = ?, updated_at = ?
                WHERE active_status = TRUE
                  AND semester IN ('1', '2')
                  AND (year < ? OR (year = ? AND semester = '1' AND ?))
                """, Timestamp.from(now), Timestamp.from(now), year, year, firstSemesterEnded);
    }
}
