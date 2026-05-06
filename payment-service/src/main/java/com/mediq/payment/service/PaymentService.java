package com.mediq.payment.service;

import com.mediq.payment.config.AppointmentBookingWorkflowSignal;
import com.mediq.payment.model.PaymentEntity;
import com.mediq.payment.model.PaymentStatus;
import com.mediq.payment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final WorkflowClient workflowClient;
    private final String currency;

    public PaymentService(
            PaymentRepository paymentRepository,
            WorkflowClient workflowClient,
            @Value("${stripe.currency}") String currency) {
        this.paymentRepository = paymentRepository;
        this.workflowClient = workflowClient;
        this.currency = currency;
    }

    @Transactional
    public CreatePaymentIntentResponse createPaymentIntent(
            String appointmentId,
            String patientId,
            BigDecimal amount,
            String temporalWorkflowId) throws StripeException {

        // ── Fix 2: Duplicate guard ────────────────────────────────────────────
        // Handles Temporal retries + concurrent requests for same appointment.
        Optional<PaymentEntity> existing =
            paymentRepository.findByAppointmentId(UUID.fromString(appointmentId));

        if (existing.isPresent()) {
            PaymentEntity existingPayment = existing.get();

            // Stripe call already completed → return existing response (idempotent)
            if (existingPayment.getStripePaymentIntentId() != null) {
                log.info("Returning existing PaymentIntent for appointmentId={}",
                    appointmentId);
                return new CreatePaymentIntentResponse(
                    existingPayment.getId().toString(),
                    existingPayment.getStripeClientSecret(),
                    existingPayment.getStripePaymentIntentId()
                );
            }

            // PENDING record exists (Stripe call not completed in previous attempt)
            // Fall through to Stripe call — idempotency key handles dedup on Stripe side
            log.info("Found PENDING payment for appointmentId={} — " +
                     "retrying Stripe call with idempotency key", appointmentId);
        }

        // ── Fix 3: Save PENDING record BEFORE calling Stripe ─────────────────
        // If app crashes after this and before Stripe: record stays PENDING,
        // next retry finds it and retries Stripe safely with idempotency key.
        PaymentEntity payment;
        try {
            payment = existing.orElseGet(() -> {
                PaymentEntity newPayment = new PaymentEntity();
                newPayment.setAppointmentId(UUID.fromString(appointmentId));
                newPayment.setPatientId(UUID.fromString(patientId));
                newPayment.setAmount(amount);
                newPayment.setCurrency(currency);
                newPayment.setStatus(PaymentStatus.PENDING);
                newPayment.setTemporalWorkflowId(temporalWorkflowId);
                return paymentRepository.save(newPayment);
            });
        } catch (DataIntegrityViolationException e) {
            // Fix 5: Race condition — another thread inserted first; fetch & proceed
            log.warn("Race condition on appointmentId={} — fetching existing record",
                appointmentId);
            payment = paymentRepository
                .findByAppointmentId(UUID.fromString(appointmentId))
                .orElseThrow(() -> new RuntimeException(
                    "Payment record missing after constraint violation: " + appointmentId));

            if (payment.getStripePaymentIntentId() != null) {
                return new CreatePaymentIntentResponse(
                    payment.getId().toString(),
                    payment.getStripeClientSecret(),
                    payment.getStripePaymentIntentId()
                );
            }
        }

        log.info("Calling Stripe for appointmentId={} paymentId={}",
            appointmentId, payment.getId());

        // ── Fix 1: Stripe idempotency key ─────────────────────────────────────
        // Same appointmentId → same key → Stripe returns SAME PaymentIntent.
        // Temporal retries never produce a duplicate charge.
        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountInPaise)
            .setCurrency(currency)
            .putMetadata("appointmentId", appointmentId)
            .putMetadata("patientId", patientId)
            .putMetadata("temporalWorkflowId", temporalWorkflowId)
            .putMetadata("mediqPaymentId", payment.getId().toString())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .build())
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey("mediq-appt-" + appointmentId)
            .build();

        PaymentIntent paymentIntent;
        try {
            paymentIntent = PaymentIntent.create(params, options);
            log.info("Stripe PaymentIntent created: {} for appointmentId: {}",
                paymentIntent.getId(), appointmentId);
        } catch (StripeException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            log.error("Stripe call failed for appointmentId={}: {}",
                appointmentId, e.getMessage());
            throw e;
        }

        // ── Update record with Stripe details ─────────────────────────────────
        payment.setStripePaymentIntentId(paymentIntent.getId());
        payment.setStripeClientSecret(paymentIntent.getClientSecret());
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        return new CreatePaymentIntentResponse(
            payment.getId().toString(),
            paymentIntent.getClientSecret(),
            paymentIntent.getId()
        );
    }

    @Transactional
    public void handleStripeWebhook(String paymentIntentId, boolean success,
                                    String failureReason) {
        paymentRepository.findByStripePaymentIntentId(paymentIntentId)
            .ifPresent(payment -> {
                payment.setStatus(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
                if (!success) payment.setFailureReason(failureReason);
                paymentRepository.save(payment);

                log.info("Payment {} appointmentId={} success={}",
                    paymentIntentId, payment.getAppointmentId(), success);

                sendTemporalSignal(payment.getTemporalWorkflowId(), paymentIntentId, success);
            });
    }

    private void sendTemporalSignal(String workflowId, String paymentIntentId,
                                    boolean success) {
        try {
            AppointmentBookingWorkflowSignal workflow =
                workflowClient.newWorkflowStub(
                    AppointmentBookingWorkflowSignal.class, workflowId);
            workflow.paymentCompleted(paymentIntentId, success);
            log.info("Temporal signal sent to workflowId={} success={}", workflowId, success);
        } catch (Exception e) {
            log.error("Failed to send Temporal signal to workflowId={}: {}",
                workflowId, e.getMessage());
        }
    }
}
