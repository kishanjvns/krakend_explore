package com.mediq.appointment.temporal.workflow;

import com.mediq.appointment.temporal.activity.AppointmentActivities;
import com.mediq.appointment.temporal.activity.NotificationActivities;
import com.mediq.appointment.temporal.activity.PaymentActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class AppointmentBookingWorkflowImpl implements AppointmentBookingWorkflow {

    private static final Logger log =
        Workflow.getLogger(AppointmentBookingWorkflowImpl.class);

    private final AppointmentActivities appointmentActivities =
        Workflow.newActivityStub(AppointmentActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .build())
                .build());

    private final PaymentActivities paymentActivities =
        Workflow.newActivityStub(PaymentActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .build())
                .build());

    private final NotificationActivities notificationActivities =
        Workflow.newActivityStub(NotificationActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .build())
                .build());

    // Workflow state — plain fields are safe in Temporal (single-threaded replay)
    private String currentStatus = "INITIATED";
    private boolean paymentReceived = false;
    private boolean paymentSuccess = false;
    private String paymentIntentId;

    @Override
    public BookingResult bookAppointment(BookingRequest request) {
        String appointmentId = null;

        // ── Step 1: Lock the slot ─────────────────────────────────────────────
        currentStatus = "LOCKING_SLOT";
        log.info("Locking slot: {}", request.slotId());

        try {
            appointmentId = appointmentActivities.lockSlot(
                request.slotId().toString(),
                request.patientId().toString(),
                request.doctorId().toString());
        } catch (Exception e) {
            currentStatus = "FAILED";
            return BookingResult.failed("Slot not available: " + e.getMessage());
        }

        // ── Step 2: Create Stripe PaymentIntent ───────────────────────────────
        currentStatus = "CREATING_PAYMENT";
        log.info("Creating payment intent for appointmentId: {}", appointmentId);

        try {
            paymentActivities.createPaymentIntent(
                appointmentId,
                request.patientId().toString(),
                request.amount());
        } catch (Exception e) {
            currentStatus = "COMPENSATING";
            appointmentActivities.releaseSlot(request.slotId().toString());
            return BookingResult.failed("Payment setup failed: " + e.getMessage());
        }

        // ── Step 3: Wait for Stripe webhook (max 10 minutes) ─────────────────
        currentStatus = "AWAITING_PAYMENT";
        log.info("Waiting for payment confirmation, appointmentId: {}", appointmentId);

        boolean paymentConfirmed = Workflow.await(
            Duration.ofMinutes(10),
            () -> this.paymentReceived);

        if (!paymentConfirmed || !paymentSuccess) {
            currentStatus = "COMPENSATING";
            log.warn("Payment failed/timed out for appointmentId: {}", appointmentId);
            appointmentActivities.cancelAppointment(
                appointmentId, "Payment not completed within 10 minutes");
            appointmentActivities.releaseSlot(request.slotId().toString());
            return BookingResult.failed("Payment failed or timed out");
        }

        // ── Step 4: Confirm appointment ───────────────────────────────────────
        currentStatus = "CONFIRMING";
        appointmentActivities.confirmAppointment(appointmentId, paymentIntentId);

        // ── Step 5: Send notification (non-critical) ──────────────────────────
        currentStatus = "NOTIFYING";
        try {
            notificationActivities.sendAppointmentConfirmation(
                request.patientEmail(),
                appointmentId,
                request.doctorId().toString());
        } catch (Exception e) {
            log.warn("Notification failed for appointmentId: {}, error: {}",
                appointmentId, e.getMessage());
        }

        currentStatus = "CONFIRMED";
        return BookingResult.success(appointmentId);
    }

    @Override
    public void paymentCompleted(String paymentIntentId, boolean success) {
        this.paymentIntentId = paymentIntentId;
        this.paymentSuccess = success;
        this.paymentReceived = true;
        log.info("Payment signal received: paymentIntentId={}, success={}",
            paymentIntentId, success);
    }

    @Override
    public String getBookingStatus() {
        return currentStatus;
    }
}
