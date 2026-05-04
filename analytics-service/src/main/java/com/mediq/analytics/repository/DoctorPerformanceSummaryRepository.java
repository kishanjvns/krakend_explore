package com.mediq.analytics.repository;

import com.mediq.analytics.model.DoctorPerformanceSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DoctorPerformanceSummaryRepository
        extends JpaRepository<DoctorPerformanceSummaryEntity, DoctorPerformanceSummaryEntity.DoctorDateId> {

    List<DoctorPerformanceSummaryEntity> findBySummaryDate(LocalDate summaryDate);
}
