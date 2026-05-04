package com.mediq.appointment.repository;

import com.mediq.appointment.model.AppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {

    List<AppointmentEntity> findByPatientId(UUID patientId);

    List<AppointmentEntity> findByDoctorId(UUID doctorId);
}
