package com.mediq.doctor.repository;

import com.mediq.doctor.model.DoctorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorProfileRepository extends JpaRepository<DoctorProfileEntity, UUID> {

    Optional<DoctorProfileEntity> findByUserId(UUID userId);
}
