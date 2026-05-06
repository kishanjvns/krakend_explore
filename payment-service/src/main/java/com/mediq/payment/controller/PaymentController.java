package com.mediq.payment.controller;

import com.mediq.payment.model.PaymentEntity;
import com.mediq.payment.repository.PaymentRepository;
import com.mediq.payment.service.CreatePaymentIntentResponse;
import com.mediq.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final String webhookSecret;

    public PaymentController(
            PaymentService paymentService,
            PaymentRepository paymentRepository,
            @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.webhookSecret = webhookSecret;
    }

    // Called by Temporal PaymentActivitiesImpl (appointment-service)
    @PostMapping("/payments/intent")
    public ResponseEntity<Map<String, String>> createPaymentIntent(
            @RequestBody Map<String, Object> request) throws Exception {

        CreatePaymentIntentResponse response = paymentService.createPaymentIntent(
            (String) request.get("appointmentId"),
            (String) request.get("patientId"),
            new BigDecimal(request.get("amount").toString()),
            (String) request.get("temporalWorkflowId")
        );

        return ResponseEntity.ok(Map.of(
            "paymentId",       response.paymentId(),
            "clientSecret",    response.clientSecret(),
            "paymentIntentId", response.paymentIntentId()
        ));
    }

    // GET payment by internal payment ID — used by KrakenD /api/v1/payments/{paymentId}
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPayment(
            @PathVariable UUID paymentId) {
        return paymentRepository.findById(paymentId)
            .map(p -> ResponseEntity.ok(Map.<String, Object>of(
                "paymentId",             p.getId().toString(),
                "appointmentId",         p.getAppointmentId().toString(),
                "stripePaymentIntentId", p.getStripePaymentIntentId() != null
                                            ? p.getStripePaymentIntentId() : "",
                "amount",                p.getAmount(),
                "currency",              p.getCurrency(),
                "status",                p.getStatus().name()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    // Called by Stripe CLI webhook forwarder — NOT exposed through KrakenD
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        log.info("Stripe webhook received: {}", event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent pi = (PaymentIntent) event
                    .getDataObjectDeserializer().getObject().orElseThrow();
                log.info("Payment succeeded: {}", pi.getId());
                paymentService.handleStripeWebhook(pi.getId(), true, null);
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent pi = (PaymentIntent) event
                    .getDataObjectDeserializer().getObject().orElseThrow();
                String failureMsg = pi.getLastPaymentError() != null
                    ? pi.getLastPaymentError().getMessage() : "Payment failed";
                log.warn("Payment failed: {} reason: {}", pi.getId(), failureMsg);
                paymentService.handleStripeWebhook(pi.getId(), false, failureMsg);
            }
            default -> log.debug("Unhandled Stripe event: {}", event.getType());
        }

        return ResponseEntity.ok("received");
    }
}
