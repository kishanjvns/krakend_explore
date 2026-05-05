package com.mediq.event;

import java.time.Instant;
import java.util.UUID;

public record UserEvent(
    String eventId,
    String eventType,
    String userId,
    String keycloakId,
    String userType,
    String firstName,
    String lastName,
    String primaryEmail,
    String primaryPhone,
    String verificationStatus,
    Instant occurredAt
) {
    public static UserEvent of(String eventType, String userId,
            String keycloakId, String userType, String firstName,
            String lastName, String email, String phone,
            String verificationStatus) {
        return new UserEvent(
            UUID.randomUUID().toString(),
            eventType,
            userId,
            keycloakId,
            userType,
            firstName,
            lastName,
            email,
            phone,
            verificationStatus,
            Instant.now()
        );
    }
}
