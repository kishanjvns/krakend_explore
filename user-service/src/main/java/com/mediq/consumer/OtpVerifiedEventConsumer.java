package com.mediq.consumer;

import com.mediq.event.OtpVerifiedEvent;
import com.mediq.model.UserEntity;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OtpVerifiedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OtpVerifiedEventConsumer.class);

    private final UserRepository userRepository;

    public OtpVerifiedEventConsumer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.otp-events}",
        groupId = "mediq-user-otp-verified-group",
        containerFactory = "otpVerifiedContainerFactory"
    )
    @Transactional
    public void onOtpVerified(OtpVerifiedEvent event, Acknowledgment ack) {
        if (!"OTP_VERIFIED".equals(event.eventType())) {
            ack.acknowledge();
            return;
        }

        log.info("OTP_VERIFIED received → userId={}", event.userId());

        try {
            UUID userId = UUID.fromString(event.userId());
            userRepository.findById(userId).ifPresent(user -> {
                user.setVerified(true);
                userRepository.save(user);
                log.info("User marked verified → userId={}", userId);
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process OTP_VERIFIED → userId={} error={}",
                event.userId(), e.getMessage());
        }
    }
}
