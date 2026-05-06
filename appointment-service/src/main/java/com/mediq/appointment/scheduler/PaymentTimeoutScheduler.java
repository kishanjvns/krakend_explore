package com.mediq.appointment.scheduler;

import com.mediq.appointment.model.AppointmentEntity;
import com.mediq.appointment.model.AppointmentStatus;
import com.mediq.appointment.model.SlotStatus;
import com.mediq.appointment.repository.AppointmentRepository;
import com.mediq.appointment.repository.AppointmentSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class PaymentTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutScheduler.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String appointmentEventsTopic;

    public PaymentTimeoutScheduler(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${mediq.kafka.topic.appointment-events}") String appointmentEventsTopic) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.appointmentEventsTopic = appointmentEventsTopic;
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cancelExpiredPayments() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        List<AppointmentEntity> expired = appointmentRepository
            .findByStatusAndCreatedAtBefore(AppointmentStatus.PAYMENT_FAILED, cutoff);

        if (expired.isEmpty()) {
            log.debug("No expired payment appointments found");
            return;
        }

        log.warn("Auto-cancelling {} expired payment appointments", expired.size());

        expired.forEach(appointment -> {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment.setCancelledAt(Instant.now());
            appointment.setCancellationReason(
                "Payment not completed within 24 hours — auto-cancelled");
            appointmentRepository.save(appointment);

            slotRepository.findById(appointment.getSlotId()).ifPresent(slot -> {
                slot.setStatus(SlotStatus.AVAILABLE);
                slotRepository.save(slot);
            });

            Map<String, String> event = Map.of(
                "eventType",     "AppointmentAutoCalculated",
                "appointmentId", appointment.getId().toString(),
                "patientId",     appointment.getPatientId().toString(),
                "doctorId",      appointment.getDoctorId().toString(),
                "reason",        "Payment not completed within 24 hours"
            );
            kafkaTemplate.send(appointmentEventsTopic,
                appointment.getId().toString(), event);

            log.info("Auto-cancelled appointmentId={}", appointment.getId());
        });
    }
}
