package com.mediq.appointment.repository;

import com.mediq.appointment.model.DoctorProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DoctorProjectionRepository extends JpaRepository<DoctorProjectionEntity, UUID> {
}
