package com.tikitaka.space.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "space.auto-archive.enabled", havingValue = "true", matchIfMissing = true)
public class SpaceAutoArchiveScheduler {
    private static final Logger log = LoggerFactory.getLogger(SpaceAutoArchiveScheduler.class);
    private final SpaceAutoArchiveService service;

    public SpaceAutoArchiveScheduler(SpaceAutoArchiveService service) {
        this.service = service;
    }

    @Scheduled(cron = "${space.auto-archive.cron:0 0 0 1 1,7 *}", zone = "Asia/Seoul")
    public void archiveAtSemesterEnd() {
        archiveExpiredSpaces();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void archiveMissedSemesters() {
        archiveExpiredSpaces();
    }

    private void archiveExpiredSpaces() {
        int count = service.archiveExpiredSpaces();
        log.info("Space automatic archival completed. archivedCount={}", count);
    }
}
