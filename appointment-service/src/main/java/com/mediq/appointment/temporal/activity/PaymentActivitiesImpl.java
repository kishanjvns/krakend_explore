package com.mediq.appointment.temporal.activity;

import io.temporal.spring.boot.ActivityImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
@ActivityImpl(taskQueues = "appointment-booking-queue")
public class PaymentActivitiesImpl implements PaymentActivities {

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    public PaymentActivitiesImpl(
            @Value("${mediq.payment-service.url:http://payment-service:8089}")
            String paymentServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.paymentServiceUrl = paymentServiceUrl;
    }

    @Override
    public String createPaymentIntent(String appointmentId,
                                      String patientId,
                                      BigDecimal amount) {
        Map<String, Object> request = Map.of(
            "appointmentId", appointmentId,
            "patientId", patientId,
            "amount", amount,
            "currency", "inr"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
            paymentServiceUrl + "/payments/intent",
            request,
            Map.class);

        return response != null ? (String) response.get("clientSecret") : null;
    }
}
