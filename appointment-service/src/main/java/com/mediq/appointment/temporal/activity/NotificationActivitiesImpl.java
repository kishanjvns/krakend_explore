package com.mediq.appointment.temporal.activity;

import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ActivityImpl(taskQueues = "appointment-booking-queue")
public class NotificationActivitiesImpl implements NotificationActivities {

    private static final Logger log = LoggerFactory.getLogger(NotificationActivitiesImpl.class);

    private final RestTemplate restTemplate;
    private final String notificationServiceUrl;

    public NotificationActivitiesImpl(
            @Value("${mediq.notification-service.url:http://notification-service:8085}")
            String notificationServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Override
    public void sendAppointmentConfirmation(String patientEmail,
                                            String appointmentId,
                                            String doctorId) {
        try {
            Map<String, String> request = Map.of(
                "patientEmail", patientEmail,
                "appointmentId", appointmentId,
                "doctorId", doctorId,
                "type", "APPOINTMENT_CONFIRMED"
            );
            restTemplate.postForObject(
                notificationServiceUrl + "/notifications/send",
                request, Void.class);
        } catch (Exception e) {
            log.warn("Notification call failed (non-fatal): {}", e.getMessage());
            // Swallow — notification failure must not fail the booking
        }
    }
}
