package com.mediq.appointment.temporal.workflow;

import java.io.Serializable;

public record BookingResult(
    String appointmentId,
    String status,
    String message
) implements Serializable {

    public static BookingResult success(String appointmentId) {
        return new BookingResult(appointmentId, "CONFIRMED", "Appointment confirmed");
    }

    public static BookingResult failed(String reason) {
        return new BookingResult(null, "FAILED", reason);
    }
}
