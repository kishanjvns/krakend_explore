package com.mediq.appointment.repository;

import com.mediq.appointment.model.AppointmentEntity;
import com.mediq.appointment.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {

    List<AppointmentEntity> findByPatientId(UUID patientId);

    List<AppointmentEntity> findByDoctorId(UUID doctorId);

    List<AppointmentEntity> findByStatusAndCreatedAtBefore(
            AppointmentStatus status, Instant before);
}
