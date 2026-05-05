package com.mediq.appointment.event;

import com.mediq.appointment.model.PatientProjectionEntity;
import com.mediq.appointment.repository.PatientProjectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final PatientProjectionRepository patientProjectionRepository;

    public UserEventConsumer(PatientProjectionRepository patientProjectionRepository) {
        this.patientProjectionRepository = patientProjectionRepository;
    }

    @KafkaListener(topics = "${mediq.kafka.topic.user-events}",
                   groupId = "mediq-appointment-user-sync-group")
    @Transactional
    public void onUserEvent(UserEvent event, Acknowledgment ack) {
        try {
            if ("USER_REGISTERED".equals(event.eventType()) && "PATIENT".equals(event.userType())) {
                UUID userId = UUID.fromString(event.userId());
                PatientProjectionEntity projection = patientProjectionRepository
                    .findById(userId).orElse(new PatientProjectionEntity());
                projection.setUserId(userId);
                projection.setFullName(event.firstName() + " " + event.lastName());
                projection.setEmail(event.primaryEmail());
                projection.setPhone(event.primaryPhone());
                projection.setActive(true);
                projection.setUpdatedAt(Instant.now());
                patientProjectionRepository.save(projection);
                log.info("Patient projection updated for userId={}", userId);
            } else if ("USER_DEACTIVATED".equals(event.eventType())) {
                UUID userId = UUID.fromString(event.userId());
                patientProjectionRepository.findById(userId).ifPresent(p -> {
                    p.setActive(false);
                    p.setUpdatedAt(Instant.now());
                    patientProjectionRepository.save(p);
                });
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing user event: {}", e.getMessage());
            ack.acknowledge();
        }
    }
}
