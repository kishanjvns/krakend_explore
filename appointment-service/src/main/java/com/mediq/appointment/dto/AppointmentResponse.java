package com.mediq.appointment.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID slotId,
    UUID patientId,
    UUID doctorId,
    String status,
    Instant bookedAt
) {}
