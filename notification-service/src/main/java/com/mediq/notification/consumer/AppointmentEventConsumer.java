package com.mediq.notification.consumer;

import com.mediq.notification.event.AppointmentEvent;
import com.mediq.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final NotificationService notificationService;

    public AppointmentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${mediq.kafka.topic.appointment-events}",
                   groupId = "mediq-notification-appointment-group")
    public void onAppointmentEvent(AppointmentEvent event, Acknowledgment ack) {
        String idempotencyKey = event.eventId() + ":notification";
        try {
            switch (event.eventType()) {
                case "AppointmentConfirmed" -> notificationService.sendAppointmentConfirmation(event, idempotencyKey);
                case "AppointmentCancelled" -> notificationService.sendCancellationNotice(event, idempotencyKey);
                default -> log.debug("No notification handler for: {}", event.eventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Notification failed for event={}: {}", event.eventType(), e.getMessage());
            // Do NOT ack — Kafka retries via DefaultErrorHandler
        }
    }
}
