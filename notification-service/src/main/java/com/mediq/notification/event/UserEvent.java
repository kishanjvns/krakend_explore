package com.mediq.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserEvent(
        String eventId,
        String eventType,
        String userId,
        String userType,
        String firstName,
        String lastName,
        String primaryEmail,
        String primaryPhone,
        String verificationStatus,
        Instant occurredAt
) {
}
