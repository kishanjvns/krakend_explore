package com.mediq.appointment.repository;

import com.mediq.appointment.model.AppointmentOutboxEntity;
import com.mediq.appointment.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentOutboxRepository extends JpaRepository<AppointmentOutboxEntity, UUID> {

    List<AppointmentOutboxEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
