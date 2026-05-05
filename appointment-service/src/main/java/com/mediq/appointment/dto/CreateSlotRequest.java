package com.mediq.appointment.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateSlotRequest(UUID doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime) {}
