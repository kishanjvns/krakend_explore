package com.mediq.payment.repository;

import com.mediq.payment.model.PaymentOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEntity, UUID> {
}
