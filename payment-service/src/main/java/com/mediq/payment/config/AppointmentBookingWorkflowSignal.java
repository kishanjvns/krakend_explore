package com.mediq.payment.config;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;

// Mirror of AppointmentBookingWorkflow signal interface.
// payment-service uses this stub to signal the running workflow in appointment-service.
@WorkflowInterface
public interface AppointmentBookingWorkflowSignal {

    @SignalMethod
    void paymentCompleted(String paymentIntentId, boolean success);
}
