package com.mediq.appointment.temporal.activity;

import com.mediq.appointment.model.AppointmentEntity;
import com.mediq.appointment.model.AppointmentStatus;
import com.mediq.appointment.model.SlotStatus;
import com.mediq.appointment.repository.AppointmentRepository;
import com.mediq.appointment.repository.AppointmentSlotRepository;
import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@ActivityImpl(taskQueues = "appointment-booking-queue")
public class AppointmentActivitiesImpl implements AppointmentActivities {

    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentActivitiesImpl(
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    @Transactional
    public String lockSlot(String slotId, String patientId, String doctorId) {
        var slot = slotRepository
            .findByIdForUpdate(UUID.fromString(slotId))
            .orElseThrow(() -> Activity.wrap(
                new RuntimeException("Slot not found: " + slotId)));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw Activity.wrap(
                new RuntimeException("Slot not available: " + slotId));
        }

        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setSlotId(slot.getId());
        appointment.setPatientId(UUID.fromString(patientId));
        appointment.setDoctorId(slot.getDoctorId());
        appointment.setStatus(AppointmentStatus.PENDING_PAYMENT);
        appointmentRepository.save(appointment);

        return appointment.getId().toString();
    }

    @Override
    @Transactional
    public void releaseSlot(String slotId) {
        slotRepository.findById(UUID.fromString(slotId))
            .ifPresent(slot -> {
                slot.setStatus(SlotStatus.AVAILABLE);
                slotRepository.save(slot);
            });
    }

    @Override
    @Transactional
    public void confirmAppointment(String appointmentId, String paymentIntentId) {
        appointmentRepository.findById(UUID.fromString(appointmentId))
            .ifPresent(appt -> {
                appt.setStatus(AppointmentStatus.CONFIRMED);
                appt.setConfirmedAt(Instant.now());
                appt.setNotes("Payment: " + paymentIntentId);
                appointmentRepository.save(appt);
            });
    }

    @Override
    @Transactional
    public void cancelAppointment(String appointmentId, String reason) {
        appointmentRepository.findById(UUID.fromString(appointmentId))
            .ifPresent(appt -> {
                appt.setStatus(AppointmentStatus.CANCELLED);
                appt.setCancelledAt(Instant.now());
                appt.setCancellationReason(reason);
                appointmentRepository.save(appt);
            });
    }
}
