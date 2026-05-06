package com.mediq.notification.consumer;

import com.mediq.notification.event.PaymentNotificationEvent;
import com.mediq.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final NotificationService notificationService;

    public PaymentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.payment-events}",
        groupId = "mediq-notification-payment-group"
    )
    public void onPaymentEvent(PaymentNotificationEvent event, Acknowledgment ack) {
        log.info("Payment event → type={} appointmentId={}",
            event.eventType(), event.appointmentId());

        try {
            switch (event.eventType()) {
                case "PAYMENT_FAILED" ->
                    notificationService.sendPaymentFailed(
                        null,
                        event.appointmentId(),
                        event.amount(),
                        event.failureReason());

                case "PAYMENT_SUCCEEDED" ->
                    log.info("Payment succeeded for appointmentId={} — confirmation sent via Temporal workflow",
                        event.appointmentId());

                default -> log.debug("Unhandled payment event: {}", event.eventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Payment notification failed → appointmentId={} error={}",
                event.appointmentId(), e.getMessage());
            ack.acknowledge();
        }
    }
}
