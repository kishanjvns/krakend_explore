package com.mediq.appointment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BookAppointmentRequest(
    UUID slotId,
    UUID patientId,
    UUID doctorId,
    BigDecimal amount
) {}
