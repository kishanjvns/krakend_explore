package com.mediq.appointment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediq.appointment.dto.AppointmentResponse;
import com.mediq.appointment.dto.BookAppointmentRequest;
import com.mediq.appointment.dto.CreateSlotRequest;
import com.mediq.appointment.dto.SlotResponse;
import com.mediq.appointment.event.AppointmentEvent;
import com.mediq.appointment.exception.SlotNotAvailableException;
import com.mediq.appointment.model.*;
import com.mediq.appointment.repository.AppointmentOutboxRepository;
import com.mediq.appointment.repository.AppointmentRepository;
import com.mediq.appointment.repository.AppointmentSlotRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               AppointmentSlotRepository slotRepository,
                               AppointmentOutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        AppointmentSlotEntity slot = slotRepository.findByIdForUpdate(request.slotId())
            .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.slotId()));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotNotAvailableException(request.slotId());
        }

        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setSlotId(slot.getId());
        appointment.setPatientId(request.patientId());
        appointment.setDoctorId(slot.getDoctorId());
        appointment.setStatus(AppointmentStatus.PENDING_PAYMENT);
        appointmentRepository.save(appointment);

        slot.setStatus(SlotStatus.BOOKED);
        slot.setUpdatedAt(Instant.now());
        slotRepository.save(slot);

        writeToOutbox(appointment, "AppointmentBooked");
        log.info("Appointment booked id={} slotId={} patientId={}",
            appointment.getId(), slot.getId(), request.patientId());

        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse confirmAppointment(UUID appointmentId) {
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + appointmentId));

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedAt(Instant.now());
        appointment.setUpdatedAt(Instant.now());
        appointmentRepository.save(appointment);

        writeToOutbox(appointment, "AppointmentConfirmed");
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse cancelAppointment(UUID appointmentId, String reason) {
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + appointmentId));

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(Instant.now());
        appointment.setCancellationReason(reason);
        appointment.setUpdatedAt(Instant.now());
        appointmentRepository.save(appointment);

        slotRepository.findById(appointment.getSlotId()).ifPresent(slot -> {
            slot.setStatus(SlotStatus.AVAILABLE);
            slot.setUpdatedAt(Instant.now());
            slotRepository.save(slot);
        });

        writeToOutbox(appointment, "AppointmentCancelled");
        return toResponse(appointment);
    }

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request) {
        AppointmentSlotEntity slot = new AppointmentSlotEntity();
        slot.setDoctorId(request.doctorId());
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(slot);
        log.info("Slot created id={} doctorId={} date={}", slot.getId(), request.doctorId(), request.slotDate());
        return new SlotResponse(slot.getId(), slot.getDoctorId(), slot.getSlotDate(),
            slot.getStartTime(), slot.getEndTime(), slot.getStatus().name());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(UUID patientId, UUID doctorId) {
        List<AppointmentEntity> list;
        if (patientId != null) {
            list = appointmentRepository.findByPatientId(patientId);
        } else if (doctorId != null) {
            list = appointmentRepository.findByDoctorId(doctorId);
        } else {
            list = List.of();
        }
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
            .map(this::toResponse)
            .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + appointmentId));
    }

    private void writeToOutbox(AppointmentEntity appointment, String eventType) {
        try {
            AppointmentEvent event = AppointmentEvent.of(
                eventType,
                appointment.getId().toString(),
                appointment.getSlotId().toString(),
                appointment.getPatientId().toString(),
                appointment.getDoctorId().toString(),
                appointment.getStatus().name()
            );
            AppointmentOutboxEntity outbox = new AppointmentOutboxEntity();
            outbox.setAggregateId(appointment.getId());
            outbox.setEventType(eventType);
            outbox.setDestination("mediq.appointment.events");
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event: " + e.getMessage(), e);
        }
    }

    private AppointmentResponse toResponse(AppointmentEntity a) {
        return new AppointmentResponse(a.getId(), a.getSlotId(), a.getPatientId(),
            a.getDoctorId(), a.getStatus().name(), a.getBookedAt());
    }
}
