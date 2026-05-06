package com.mediq.notification.consumer;

import com.mediq.notification.event.OtpRequestedEvent;
import com.mediq.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OtpEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OtpEventConsumer.class);

    private final NotificationService notificationService;

    public OtpEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.user-events}",
        groupId = "mediq-notification-otp-group"
    )
    public void onUserEvent(OtpRequestedEvent event, Acknowledgment ack) {
        if (!"OTP_REQUESTED".equals(event.eventType())) {
            ack.acknowledge();
            return;
        }

        log.info("OTP event received → userId={} contactType={}",
            event.userId(), event.contactType());

        try {
            if ("EMAIL".equals(event.contactType())) {
                notificationService.sendOtpViaEmail(
                    event.userId(),
                    event.destination(),
                    event.userName(),
                    event.otp(),
                    event.expiresInMinutes()
                );
            } else if ("PHONE".equals(event.contactType())) {
                notificationService.sendOtpViaPhone(
                    event.userId(),
                    event.destination(),
                    event.otp(),
                    event.expiresInMinutes()
                );
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("OTP notification failed → userId={} error={}",
                event.userId(), e.getMessage());
            // Do NOT ack — Kafka retries
        }
    }
}
