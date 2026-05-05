package com.mediq.emr.repository;

import com.mediq.emr.model.PatientSummaryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientSummaryRepository extends JpaRepository<PatientSummaryEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PatientSummaryEntity s WHERE s.patientId = :patientId")
    Optional<PatientSummaryEntity> findByPatientIdForUpdate(@Param("patientId") String patientId);
}
