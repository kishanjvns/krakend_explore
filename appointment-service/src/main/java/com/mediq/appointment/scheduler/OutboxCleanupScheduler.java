package com.mediq.appointment.scheduler;

import com.mediq.appointment.repository.AppointmentOutboxRepository;
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
    private final AppointmentOutboxRepository outboxRepository;

    public OutboxCleanupScheduler(AppointmentOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredOutboxEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Appointment outbox cleanup: deleted {} rows older than 7 days", deleted);
    }
}
