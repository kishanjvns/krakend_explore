package com.mediq.appointment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NotificationActivities {

    @ActivityMethod
    void sendAppointmentConfirmation(String patientEmail,
                                     String appointmentId,
                                     String doctorId);
}
