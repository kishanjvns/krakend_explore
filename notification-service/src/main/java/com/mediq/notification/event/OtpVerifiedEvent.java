package com.mediq.notification.event;

import java.time.Instant;
import java.util.UUID;

public record OtpVerifiedEvent(
        String eventId,
        String eventType,
        String userId,
        Instant occurredAt
) {
    public static OtpVerifiedEvent of(String userId) {
        return new OtpVerifiedEvent(
            UUID.randomUUID().toString(),
            "OTP_VERIFIED",
            userId,
            Instant.now()
        );
    }
}
