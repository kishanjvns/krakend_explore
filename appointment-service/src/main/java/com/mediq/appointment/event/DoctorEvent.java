package com.mediq.appointment.event;

import java.time.Instant;

public record DoctorEvent(
    String eventId,
    String eventType,
    String doctorId,
    String userId,
    String firstName,
    String lastName,
    String primarySpecialization,
    Instant occurredAt
) {}
