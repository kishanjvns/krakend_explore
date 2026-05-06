package com.mediq.appointment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;

@ActivityInterface
public interface PaymentActivities {

    @ActivityMethod
    String createPaymentIntent(String appointmentId,
                               String patientId,
                               BigDecimal amount);
}
