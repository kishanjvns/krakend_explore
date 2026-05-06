package com.mediq.notification.service;

import com.mediq.notification.event.AppointmentEvent;
import com.mediq.notification.event.UserEvent;
import com.mediq.notification.model.Channel;
import com.mediq.notification.model.NotificationEntity;
import com.mediq.notification.model.NotificationStatus;
import com.mediq.notification.repository.NotificationRepository;
import com.mediq.notification.strategy.email.EmailSender;
import com.mediq.notification.strategy.otp.OtpSender;
import com.mediq.notification.strategy.sms.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailGateway emailGateway;
    private final SmsGateway smsGateway;
    private final OtpSender otpSender;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final TemplateEngine templateEngine;
    private final String fromName;

    public NotificationService(
            NotificationRepository notificationRepository,
            EmailGateway emailGateway,
            SmsGateway smsGateway,
            OtpSender otpSender,
            EmailSender emailSender,
            SmsSender smsSender,
            TemplateEngine templateEngine,
            @Value("${mediq.notification.from-name}") String fromName) {
        this.notificationRepository = notificationRepository;
        this.emailGateway = emailGateway;
        this.smsGateway = smsGateway;
        this.otpSender = otpSender;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.templateEngine = templateEngine;
        this.fromName = fromName;
    }

    // ── Legacy stub-based methods (kept for backward compat) ─────────────────

    @Transactional
    public void sendWelcomeNotification(UserEvent event, String idempotencyKey) {
        if (notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Duplicate welcome notification skipped. key={}", idempotencyKey);
            return;
        }

        UUID recipientUserId = toUuid(event.userId());
        String body = "Welcome to mediq, " + event.firstName() + "!";

        NotificationEntity notification = new NotificationEntity();
        notification.setRecipientUserId(recipientUserId);
        notification.setRecipientEmail(event.primaryEmail());
        notification.setChannel(Channel.EMAIL);
        notification.setNotificationType("WELCOME");
        notification.setSubject("Welcome to mediq");
        notification.setBody(body);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setIdempotencyKey(idempotencyKey);
        notification = notificationRepository.save(notification);

        try {
            emailGateway.send(event.primaryEmail(), "Welcome to mediq", body);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send welcome email. userId={} error={}", event.userId(), e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(e.getMessage());
        }
        notificationRepository.save(notification);
    }

    @Transactional
    public void sendAppointmentConfirmation(AppointmentEvent event, String idempotencyKey) {
        if (notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Duplicate appointment confirmation skipped. key={}", idempotencyKey);
            return;
        }

        UUID recipientUserId = toUuid(event.patientId());
        String body = "Your appointment is confirmed. Appointment ID: " + event.appointmentId();

        NotificationEntity notification = new NotificationEntity();
        notification.setRecipientUserId(recipientUserId);
        notification.setChannel(Channel.SMS);
        notification.setNotificationType("APPOINTMENT_CONFIRMED");
        notification.setSubject("Appointment Confirmed");
        notification.setBody(body);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setIdempotencyKey(idempotencyKey);
        notification = notificationRepository.save(notification);

        try {
            smsGateway.send(null, body);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send appointment confirmation SMS. appointmentId={} error={}",
                    event.appointmentId(), e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(e.getMessage());
        }
        notificationRepository.save(notification);
    }

    @Transactional
    public void sendCancellationNotice(AppointmentEvent event, String idempotencyKey) {
        if (notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Duplicate cancellation notice skipped. key={}", idempotencyKey);
            return;
        }

        UUID recipientUserId = toUuid(event.patientId());
        String body = "Your appointment has been cancelled. Appointment ID: " + event.appointmentId();

        NotificationEntity notification = new NotificationEntity();
        notification.setRecipientUserId(recipientUserId);
        notification.setChannel(Channel.SMS);
        notification.setNotificationType("APPOINTMENT_CANCELLED");
        notification.setSubject("Appointment Cancelled");
        notification.setBody(body);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setIdempotencyKey(idempotencyKey);
        notification = notificationRepository.save(notification);

        try {
            smsGateway.send(null, body);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send cancellation SMS. appointmentId={} error={}",
                    event.appointmentId(), e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(e.getMessage());
        }
        notificationRepository.save(notification);
    }

    // ── Strategy-based methods ────────────────────────────────────────────────

    public void sendOtpViaPhone(String userId, String phone, String otp, int expiresIn) {
        otpSender.sendOtp(userId, phone, otp, expiresIn);
    }

    public void sendOtpViaEmail(String userId, String email, String userName,
                                 String otp, int expiresIn) {
        Context ctx = new Context();
        ctx.setVariable("userName", userName);
        ctx.setVariable("otp", otp);
        ctx.setVariable("expiresIn", expiresIn);

        String body = templateEngine.process("email/otp-email", ctx);
        emailSender.sendEmail(email, "mediq — Your OTP Verification Code", body, true);
        log.info("OTP email sent → userId={} email={}", userId, email);
    }

    public void sendPaymentFailed(String patientEmail, String appointmentId,
                                   String amount, String failureReason) {
        Context ctx = new Context();
        ctx.setVariable("patientName", "Patient");
        ctx.setVariable("appointmentId", appointmentId);
        ctx.setVariable("amount", amount);
        ctx.setVariable("failureReason", failureReason != null ? failureReason : "Payment declined");
        ctx.setVariable("retryLink", "#");

        String body = templateEngine.process("email/payment-failed-email", ctx);

        if (patientEmail != null) {
            emailSender.sendEmail(patientEmail,
                "mediq — Action Required: Payment Failed", body, true);
        }

        String smsBody = String.format(
            "mediq: Payment of Rs.%s failed. Retry within 24hrs or appointment will be cancelled.",
            amount);
        smsSender.sendSms(null, smsBody);

        log.info("Payment failed notifications sent → appointmentId={}", appointmentId);
    }

    public void sendAppointmentAutoCancelled(String appointmentId, String reason) {
        String smsBody = String.format(
            "mediq: Appointment %s has been auto-cancelled. Reason: %s", appointmentId, reason);
        smsSender.sendSms(null, smsBody);
        log.info("Auto-cancel notifications sent → appointmentId={}", appointmentId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID toUuid(String value) {
        if (value == null) return UUID.randomUUID();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(value.getBytes());
        }
    }
}
