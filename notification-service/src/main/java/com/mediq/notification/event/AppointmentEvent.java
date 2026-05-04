package com.mediq.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
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
}
