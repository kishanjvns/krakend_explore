package com.mediq.event;

import java.time.Instant;
import java.util.UUID;

public record OtpRequestedEvent(
        String eventId,
        String eventType,
        String userId,
        String contactType,
        String destination,
        String userName,
        int expiresInMinutes,
        Instant occurredAt
) {
    public static OtpRequestedEvent of(String userId, String contactType,
            String destination, String userName) {
        return new OtpRequestedEvent(
            UUID.randomUUID().toString(),
            "OTP_REQUESTED",
            userId,
            contactType,
            destination,
            userName,
            5,
            Instant.now()
        );
    }
}
