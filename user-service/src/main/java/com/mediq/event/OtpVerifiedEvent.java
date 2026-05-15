package com.mediq.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OtpVerifiedEvent(
        String eventId,
        String eventType,
        String userId,
        Instant occurredAt
) {}
