package com.tikitaka.space.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class SpaceAutoArchiveSchedulerTests {
    @Test
    void runsOnlyAtTheTwoSemesterBoundaries() {
        CronExpression cron = CronExpression.parse("0 0 0 1 1,7 *");
        assertThat(cron.next(LocalDateTime.of(2026, 1, 1, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 7, 1, 0, 0)))
                .isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    @Test
    void scheduledRunAndStartupBothProcessExpiredSpaces() {
        SpaceAutoArchiveService service = mock(SpaceAutoArchiveService.class);
        SpaceAutoArchiveScheduler scheduler = new SpaceAutoArchiveScheduler(service);

        scheduler.archiveAtSemesterEnd();
        scheduler.archiveMissedSemesters();

        verify(service, times(2)).archiveExpiredSpaces();
    }
}
