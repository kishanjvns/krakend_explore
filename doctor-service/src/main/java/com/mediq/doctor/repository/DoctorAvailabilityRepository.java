package com.mediq.doctor.repository;

import com.mediq.doctor.model.DoctorAvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailabilityEntity, UUID> {

    List<DoctorAvailabilityEntity> findByDoctorId(UUID doctorId);

    List<DoctorAvailabilityEntity> findByDoctorIdAndActive(UUID doctorId, boolean active);
}
