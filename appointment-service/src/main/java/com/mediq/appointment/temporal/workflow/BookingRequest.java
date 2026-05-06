package com.mediq.appointment.temporal.workflow;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record BookingRequest(
    UUID patientId,
    UUID doctorId,
    UUID slotId,
    BigDecimal amount,
    String patientEmail
) implements Serializable {}
