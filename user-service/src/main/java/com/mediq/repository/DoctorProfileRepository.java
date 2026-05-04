package com.mediq.repository;

import com.mediq.model.DoctorProfileEntity;
import com.mediq.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfileEntity, UUID> {
    Optional<DoctorProfileEntity> findByUserId(UUID userId);
    List<DoctorProfileEntity> findByVerificationStatus(VerificationStatus status);
}
