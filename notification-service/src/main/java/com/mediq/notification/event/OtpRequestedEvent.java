package com.mediq.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OtpRequestedEvent(
        String eventId,
        String eventType,
        String userId,
        String contactType,
        String destination,
        String userName,
        String otp,
        int expiresInMinutes,
        Instant occurredAt
) {}
