package com.mediq.appointment.temporal.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AppointmentBookingWorkflow {

    @WorkflowMethod
    BookingResult bookAppointment(BookingRequest request);

    @SignalMethod
    void paymentCompleted(String paymentIntentId, boolean success);

    @QueryMethod
    String getBookingStatus();
}
