package com.mediq.appointment.repository;

import com.mediq.appointment.model.AppointmentOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.UUID;

public interface AppointmentOutboxRepository extends JpaRepository<AppointmentOutboxEntity, UUID> {
    @Modifying
    @Query("DELETE FROM AppointmentOutboxEntity o WHERE o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
