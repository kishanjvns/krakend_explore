package com.mediq.doctor.repository;

import com.mediq.doctor.model.DoctorSearchViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DoctorSearchViewRepository extends JpaRepository<DoctorSearchViewEntity, UUID> {

    List<DoctorSearchViewEntity> findByPrimarySpecialization(String specialization);

    List<DoctorSearchViewEntity> findByVerifiedAndActive(boolean verified, boolean active);

    @Query("SELECT d FROM DoctorSearchViewEntity d WHERE " +
           "(:specialization IS NULL OR d.primarySpecialization = :specialization) AND " +
           "(:verified IS NULL OR d.verified = :verified) AND " +
           "d.active = true")
    List<DoctorSearchViewEntity> searchDoctors(
            @Param("specialization") String specialization,
            @Param("verified") Boolean verified);
}
