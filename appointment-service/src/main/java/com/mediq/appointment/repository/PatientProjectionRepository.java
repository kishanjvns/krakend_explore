package com.mediq.appointment.repository;

import com.mediq.appointment.model.PatientProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientProjectionRepository extends JpaRepository<PatientProjectionEntity, UUID> {
}
