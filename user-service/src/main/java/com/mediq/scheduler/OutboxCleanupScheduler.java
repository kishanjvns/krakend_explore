package com.mediq.scheduler;

import com.mediq.repository.UserOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);
    private final UserOutboxRepository outboxRepository;

    public OutboxCleanupScheduler(UserOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredOutboxEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteByCreatedAtBefore(cutoff);
        log.info("User outbox cleanup: deleted {} rows older than 7 days", deleted);
    }
}
