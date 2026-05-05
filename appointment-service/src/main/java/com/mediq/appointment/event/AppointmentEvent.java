package com.mediq.appointment.event;

import java.time.Instant;
import java.util.UUID;

public record AppointmentEvent(
    String eventId,
    String eventType,
    String appointmentId,
    String slotId,
    String patientId,
    String doctorId,
    String status,
    Instant occurredAt
) {
    public static AppointmentEvent of(String eventType, String appointmentId,
                                       String slotId, String patientId,
                                       String doctorId, String status) {
        return new AppointmentEvent(
            UUID.randomUUID().toString(), eventType,
            appointmentId, slotId, patientId, doctorId, status, Instant.now());
    }
}
