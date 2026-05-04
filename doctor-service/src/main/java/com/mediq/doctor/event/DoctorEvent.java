package com.mediq.doctor.event;

import com.mediq.doctor.model.DoctorProfileEntity;

import java.time.Instant;
import java.util.UUID;

public record DoctorEvent(
        String eventId,
        String eventType,
        String doctorId,
        String userId,
        String firstName,
        String lastName,
        String primarySpecialization,
        Instant occurredAt
) {
    public static DoctorEvent of(String eventType, DoctorProfileEntity profile) {
        return new DoctorEvent(
                UUID.randomUUID().toString(),
                eventType,
                profile.getId().toString(),
                profile.getUserId().toString(),
                profile.getFirstName(),
                profile.getLastName(),
                null,
                Instant.now()
        );
    }
}
