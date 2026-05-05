package com.mediq.emr.repository;

import com.mediq.emr.model.PatientEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientEventRepository extends JpaRepository<PatientEventEntity, UUID> {

    List<PatientEventEntity> findByPatientIdOrderBySequenceNumberAsc(String patientId);

    List<PatientEventEntity> findByPatientIdAndOccurredAtBeforeOrderBySequenceNumberAsc(
            String patientId, Instant before);

    @Query("SELECT MAX(e.sequenceNumber) FROM PatientEventEntity e WHERE e.patientId = :patientId")
    Optional<Long> findMaxSequenceByPatientId(@Param("patientId") String patientId);
}
