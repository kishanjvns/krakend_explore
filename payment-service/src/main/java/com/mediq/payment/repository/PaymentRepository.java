package com.mediq.payment.repository;

import com.mediq.payment.model.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<PaymentEntity> findByAppointmentId(UUID appointmentId);
}
