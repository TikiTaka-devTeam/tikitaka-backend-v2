package com.tikitaka.space.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class SpaceAutoArchivePostgresTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void seedSpaces() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("DROP TABLE IF EXISTS spaces");
        jdbc.execute("""
                CREATE TABLE spaces (
                    id INTEGER PRIMARY KEY, year INTEGER NOT NULL, semester VARCHAR(10) NOT NULL,
                    active_status BOOLEAN NOT NULL DEFAULT TRUE,
                    archived_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        jdbc.update("INSERT INTO spaces (id, year, semester) VALUES (1,2025,'2'),(2,2026,'1'),(3,2026,'2'),(4,2027,'1')");
        jdbc.update("""
                INSERT INTO spaces (id, year, semester, active_status, archived_at)
                VALUES (5,2025,'1',FALSE,'2025-07-01T00:00:00+09:00')
                """);
    }

    @Test
    void archivesOnlyExpiredActiveSpacesAtJulyBoundaryAndPreservesExistingArchiveTime() {
        SpaceAutoArchiveService before = serviceAt("2026-06-30T14:59:59Z");
        assertThat(before.archiveExpiredSpaces()).isEqualTo(1);
        assertThat(active(2)).isTrue();

        SpaceAutoArchiveService after = serviceAt("2026-06-30T15:00:00Z");
        assertThat(after.archiveExpiredSpaces()).isEqualTo(1);
        assertThat(active(2)).isFalse();
        assertThat(active(3)).isTrue();
        assertThat(active(4)).isTrue();
        assertThat(jdbc.queryForObject("SELECT archived_at FROM spaces WHERE id=2", Timestamp.class).toInstant())
                .isEqualTo(Instant.parse("2026-06-30T15:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT archived_at FROM spaces WHERE id=5", Timestamp.class).toInstant())
                .isEqualTo(Instant.parse("2025-06-30T15:00:00Z"));
        assertThat(after.archiveExpiredSpaces()).isZero();
    }

    @Test
    void archivesSecondSemesterAtJanuaryBoundaryAndCatchesUpAfterDowntime() {
        assertThat(serviceAt("2026-12-31T14:59:59Z").archiveExpiredSpaces()).isEqualTo(2);
        assertThat(active(3)).isTrue();
        assertThat(serviceAt("2026-12-31T15:00:00Z").archiveExpiredSpaces()).isEqualTo(1);
        assertThat(active(3)).isFalse();
        assertThat(active(4)).isTrue();

        jdbc.update("UPDATE spaces SET active_status=TRUE, archived_at=NULL WHERE id=3");
        assertThat(serviceAt("2027-02-15T00:00:00Z").archiveExpiredSpaces()).isEqualTo(1);
        assertThat(active(3)).isFalse();
    }

    private SpaceAutoArchiveService serviceAt(String time) {
        return new SpaceAutoArchiveService(jdbc, Clock.fixed(Instant.parse(time), ZoneOffset.UTC));
    }

    private boolean active(int id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT active_status FROM spaces WHERE id=?", Boolean.class, id));
    }
}
