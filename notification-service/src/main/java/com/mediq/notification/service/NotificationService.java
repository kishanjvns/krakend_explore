package com.mediq.notification.service;

import com.mediq.notification.event.AppointmentEvent;
import com.mediq.notification.event.UserEvent;
import com.mediq.notification.model.Channel;
import com.mediq.notification.model.NotificationEntity;
import com.mediq.notification.model.NotificationStatus;
import com.mediq.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailGateway emailGateway;
    private final SmsGateway smsGateway;

    public NotificationService(NotificationRepository notificationRepository,
                                EmailGateway emailGateway,
                                SmsGateway smsGateway) {
        this.notificationRepository = notificationRepository;
        this.emailGateway = emailGateway;
        this.smsGateway = smsGateway;
    }

    /**
     * Send a welcome email to a newly registered user.
     * Idempotent — re-entrant calls with the same idempotencyKey are silently ignored.
     */
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

    /**
     * Send an SMS confirmation when an appointment is confirmed.
     */
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

    /**
     * Send an SMS cancellation notice when an appointment is cancelled.
     */
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

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private UUID toUuid(String value) {
        if (value == null) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            // Gracefully handle non-UUID string identifiers
            return UUID.nameUUIDFromBytes(value.getBytes());
        }
    }
}
