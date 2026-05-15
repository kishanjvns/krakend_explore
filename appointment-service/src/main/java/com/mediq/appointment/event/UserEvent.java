package com.mediq.appointment.event;

import java.time.Instant;

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
) {}
