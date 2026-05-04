package com.mediq.doctor.event;

import com.mediq.doctor.service.DoctorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final DoctorService doctorService;

    public UserEventConsumer(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @KafkaListener(topics = "${mediq.kafka.topic.user-events}",
                   groupId = "mediq-doctor-user-sync-group")
    public void onUserEvent(UserEvent event, Acknowledgment ack) {
        try {
            switch (event.eventType()) {
                case "USER_REGISTERED" -> {
                    if ("DOCTOR".equals(event.userType())) {
                        doctorService.createDoctorStub(event);
                    }
                }
                case "DOCTOR_VERIFIED" -> doctorService.activateDoctorInSearch(event);
                case "USER_DEACTIVATED" -> doctorService.deactivateDoctorFromSearch(event);
                default -> log.debug("Ignoring user event type: {}", event.eventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing user event type={} userId={}: {}",
                event.eventType(), event.userId(), e.getMessage());
            ack.acknowledge();
        }
    }
}
