package com.mediq.appointment.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SlotResponse(UUID id, UUID doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, String status) {}
