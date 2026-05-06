package com.mediq.appointment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AppointmentActivities {

    @ActivityMethod
    String lockSlot(String slotId, String patientId, String doctorId);

    @ActivityMethod
    void releaseSlot(String slotId);

    @ActivityMethod
    void confirmAppointment(String appointmentId, String paymentIntentId);

    @ActivityMethod
    void cancelAppointment(String appointmentId, String reason);
}
