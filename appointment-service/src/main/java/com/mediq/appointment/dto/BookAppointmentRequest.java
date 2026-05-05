package com.mediq.appointment.dto;

import java.util.UUID;

public record BookAppointmentRequest(UUID slotId, UUID patientId) {}
