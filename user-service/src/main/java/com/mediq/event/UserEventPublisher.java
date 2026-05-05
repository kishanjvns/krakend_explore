package com.mediq.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private final String topic;

    public UserEventPublisher(
            KafkaTemplate<String, UserEvent> kafkaTemplate,
            @Value("${mediq.kafka.topic.user-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(UserEvent event) {
        // userId as partition key — all events for same user go to same partition
        CompletableFuture<SendResult<String, UserEvent>> future =
            kafkaTemplate.send(topic, event.userId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event={} for userId={}: {}",
                    event.eventType(), event.userId(), ex.getMessage());
                // TODO: Implement Outbox pattern in M2.x task
            } else {
                log.info("Published event={} for userId={} to partition={}",
                    event.eventType(), event.userId(),
                    result.getRecordMetadata().partition());
            }
        });
    }
}
