package com.mediq.doctor.repository;

import com.mediq.doctor.model.DoctorSpecializationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DoctorSpecializationRepository extends JpaRepository<DoctorSpecializationEntity, UUID> {

    List<DoctorSpecializationEntity> findByDoctorId(UUID doctorId);
}
