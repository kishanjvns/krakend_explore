package com.mediq.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediq.event.UserEvent;
import com.mediq.model.OutboxStatus;
import com.mediq.model.UserOutboxEntity;
import com.mediq.repository.UserOutboxRepository;
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
public class UserOutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserOutboxRelayScheduler.class);

    private final UserOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mediq.kafka.topic.user-events}")
    private String topic;

    public UserOutboxRelayScheduler(UserOutboxRepository outboxRepository,
                                    KafkaTemplate<String, Object> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relay() {
        outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
            .forEach(event -> {
                try {
                    UserEvent userEvent = objectMapper.readValue(event.getPayload(), UserEvent.class);
                    kafkaTemplate.send(topic, userEvent.userId(), userEvent)
                        .get(5, TimeUnit.SECONDS);
                    event.setStatus(OutboxStatus.PUBLISHED);
                    event.setPublishedAt(Instant.now());
                    outboxRepository.save(event);
                    log.info("Outbox published eventType={} userId={}", event.getEventType(), event.getAggregateId());
                } catch (Exception e) {
                    log.error("Outbox relay failed for id={}: {}", event.getId(), e.getMessage());
                    event.setStatus(OutboxStatus.FAILED);
                    outboxRepository.save(event);
                }
            });
    }
}
