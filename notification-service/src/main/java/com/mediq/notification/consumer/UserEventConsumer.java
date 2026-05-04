package com.mediq.notification.consumer;

import com.mediq.notification.event.UserEvent;
import com.mediq.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final NotificationService notificationService;

    public UserEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${mediq.kafka.topic.user-events}",
                   groupId = "mediq-notification-user-group")
    public void onUserEvent(UserEvent event, Acknowledgment ack) {
        try {
            if ("USER_REGISTERED".equals(event.eventType())) {
                String key = event.eventId() + ":welcome";
                notificationService.sendWelcomeNotification(event, key);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process user event type={}: {}", event.eventType(), e.getMessage());
            ack.acknowledge();
        }
    }
}
