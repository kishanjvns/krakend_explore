package com.mediq.notification.consumer;

import com.mediq.notification.event.OtpRequestedEvent;
import com.mediq.notification.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OtpEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OtpEventConsumer.class);

    private final OtpService otpService;

    public OtpEventConsumer(OtpService otpService) {
        this.otpService = otpService;
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

        log.info("OTP_REQUESTED event received → userId={} contactType={}",
            event.userId(), event.contactType());

        try {
            otpService.generateAndSend(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("OTP generation/send failed → userId={} error={}",
                event.userId(), e.getMessage());
            // Do NOT ack — Kafka retries
        }
    }
}
