package com.mediq.appointment.event;

import com.mediq.appointment.model.DoctorProjectionEntity;
import com.mediq.appointment.repository.DoctorProjectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class DoctorEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DoctorEventConsumer.class);

    private final DoctorProjectionRepository doctorProjectionRepository;

    public DoctorEventConsumer(DoctorProjectionRepository doctorProjectionRepository) {
        this.doctorProjectionRepository = doctorProjectionRepository;
    }

    @KafkaListener(topics = "${mediq.kafka.topic.doctor-events}",
                   groupId = "mediq-appointment-doctor-sync-group")
    @Transactional
    public void onDoctorEvent(DoctorEvent event, Acknowledgment ack) {
        try {
            UUID doctorId = UUID.fromString(event.doctorId());
            DoctorProjectionEntity projection = doctorProjectionRepository
                .findById(doctorId).orElse(new DoctorProjectionEntity());
            projection.setDoctorId(doctorId);
            projection.setUserId(UUID.fromString(event.userId()));
            projection.setFullName(event.firstName() + " " + event.lastName());
            projection.setSpecialization(event.primarySpecialization());
            projection.setUpdatedAt(Instant.now());
            doctorProjectionRepository.save(projection);
            log.info("Doctor projection updated for doctorId={}", doctorId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing doctor event: {}", e.getMessage());
            ack.acknowledge();
        }
    }
}
