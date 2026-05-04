package com.mediq.analytics.repository;

import com.mediq.analytics.model.DailyAppointmentSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyAppointmentSummaryRepository extends JpaRepository<DailyAppointmentSummaryEntity, UUID> {

    Optional<DailyAppointmentSummaryEntity> findBySummaryDate(LocalDate summaryDate);

    List<DailyAppointmentSummaryEntity> findBySummaryDateBetweenOrderBySummaryDateAsc(
            LocalDate from, LocalDate to);
}
