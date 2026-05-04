package com.mediq.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

public record SetAvailabilityRequest(
        @NotBlank(message = "Day of week must not be blank")
        @Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN",
                 message = "Day of week must be one of: MON, TUE, WED, THU, FRI, SAT, SUN")
        String dayOfWeek,

        @NotNull(message = "Start time must not be null")
        LocalTime startTime,

        @NotNull(message = "End time must not be null")
        LocalTime endTime,

        @Positive(message = "Slot duration must be positive")
        int slotDuration
) {}
