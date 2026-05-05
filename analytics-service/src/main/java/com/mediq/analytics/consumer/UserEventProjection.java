package com.mediq.analytics.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediq.analytics.repository.PlatformMetricRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class UserEventProjection {

    private static final Logger log = LoggerFactory.getLogger(UserEventProjection.class);

    private final PlatformMetricRepository metricRepository;
    private final ObjectMapper objectMapper;

    public UserEventProjection(PlatformMetricRepository metricRepository, ObjectMapper objectMapper) {
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "mediq.user.events", groupId = "mediq-analytics-user-group")
    @Transactional
    public void onUserEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            String eventType = event.path("eventType").asText();

            if ("USER_REGISTERED".equals(eventType)) {
                increment("total_registered_users");
                String role = event.path("role").asText("");
                if ("DOCTOR".equalsIgnoreCase(role)) {
                    increment("total_registered_doctors");
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to project user event offset={}: {}", record.offset(), ex.getMessage());
        }
    }

    private void increment(String metricName) {
        metricRepository.findByMetricNameForUpdate(metricName).ifPresent(m -> {
            m.setMetricValue(m.getMetricValue() + 1);
            m.setLastUpdated(Instant.now());
            metricRepository.save(m);
        });
    }
}
