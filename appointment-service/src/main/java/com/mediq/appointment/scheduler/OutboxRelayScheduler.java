package com.mediq.appointment.scheduler;

import com.mediq.appointment.model.AppointmentOutboxEntity;
import com.mediq.appointment.model.OutboxStatus;
import com.mediq.appointment.repository.AppointmentOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final AppointmentOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${mediq.kafka.topic.appointment-events}")
    private String topic;

    public OutboxRelayScheduler(AppointmentOutboxRepository outboxRepository,
                                 KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayPendingEvents() {
        for (AppointmentOutboxEntity event :
                outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)) {
            try {
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                log.info("Outbox published eventType={} aggregateId={}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Outbox relay failed for id={}: {}", event.getId(), e.getMessage());
                event.setStatus(OutboxStatus.FAILED);
                outboxRepository.save(event);
            }
        }
    }
}
