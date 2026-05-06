package com.mediq.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentNotificationEvent(
        String eventType,
        String appointmentId,
        String patientId,
        String amount,
        String currency,
        String failureReason,
        Instant occurredAt
) {}
