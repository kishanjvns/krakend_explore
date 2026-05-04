package com.mediq.appointment.repository;

import com.mediq.appointment.model.AppointmentSlotEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlotEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AppointmentSlotEntity s WHERE s.id = :id")
    Optional<AppointmentSlotEntity> findByIdForUpdate(@Param("id") UUID id);

    List<AppointmentSlotEntity> findByDoctorIdAndSlotDate(UUID doctorId, LocalDate date);
}
