package com.mediq.payment.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentNotificationEvent(
        String eventType,
        String appointmentId,
        String patientId,
        String amount,
        String currency,
        String failureReason,
        Instant occurredAt
) {
    public static PaymentNotificationEvent succeeded(String appointmentId,
            String patientId, String amount, String currency) {
        return new PaymentNotificationEvent(
            "PAYMENT_SUCCEEDED", appointmentId, patientId,
            amount, currency, null, Instant.now());
    }

    public static PaymentNotificationEvent failed(String appointmentId,
            String patientId, String amount, String currency, String failureReason) {
        return new PaymentNotificationEvent(
            "PAYMENT_FAILED", appointmentId, patientId,
            amount, currency, failureReason, Instant.now());
    }
}
