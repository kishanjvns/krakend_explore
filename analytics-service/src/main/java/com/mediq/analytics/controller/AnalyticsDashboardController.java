package com.mediq.analytics.controller;

import com.mediq.analytics.model.DailyAppointmentSummaryEntity;
import com.mediq.analytics.model.DoctorPerformanceSummaryEntity;
import com.mediq.analytics.model.PlatformMetricEntity;
import com.mediq.analytics.repository.DailyAppointmentSummaryRepository;
import com.mediq.analytics.repository.DoctorPerformanceSummaryRepository;
import com.mediq.analytics.repository.PlatformMetricRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsDashboardController {

    private final DailyAppointmentSummaryRepository dailyRepo;
    private final DoctorPerformanceSummaryRepository doctorPerfRepo;
    private final PlatformMetricRepository metricRepo;

    public AnalyticsDashboardController(DailyAppointmentSummaryRepository dailyRepo,
                                         DoctorPerformanceSummaryRepository doctorPerfRepo,
                                         PlatformMetricRepository metricRepo) {
        this.dailyRepo = dailyRepo;
        this.doctorPerfRepo = doctorPerfRepo;
        this.metricRepo = metricRepo;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('READ_ANALYTICS') or hasAuthority('READ_OWN_ANALYTICS')")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        List<PlatformMetricEntity> metrics = metricRepo.findAll();
        Map<String, Long> metricsMap = new HashMap<>();
        for (PlatformMetricEntity m : metrics) {
            metricsMap.put(m.getMetricName(), m.getMetricValue());
        }

        LocalDate today = LocalDate.now();
        Map<String, Object> todayAppointments = dailyRepo.findBySummaryDate(today)
                .<Map<String, Object>>map(s -> Map.of(
                        "date", s.getSummaryDate(),
                        "booked", s.getTotalBooked(),
                        "confirmed", s.getTotalConfirmed(),
                        "cancelled", s.getTotalCancelled(),
                        "completed", s.getTotalCompleted()
                ))
                .orElse(Map.of("date", today, "booked", 0, "confirmed", 0, "cancelled", 0, "completed", 0));

        return ResponseEntity.ok(Map.of(
                "platformMetrics", metricsMap,
                "todayAppointments", todayAppointments
        ));
    }

    @GetMapping("/appointments/daily")
    @PreAuthorize("hasAuthority('READ_ANALYTICS')")
    public ResponseEntity<List<DailyAppointmentSummaryEntity>> getDailyAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate start = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? to : LocalDate.now();
        return ResponseEntity.ok(dailyRepo.findBySummaryDateBetweenOrderBySummaryDateAsc(start, end));
    }

    @GetMapping("/doctors/performance")
    @PreAuthorize("hasAuthority('READ_ANALYTICS') or hasAuthority('READ_OWN_ANALYTICS')")
    public ResponseEntity<List<DoctorPerformanceSummaryEntity>> getDoctorPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(doctorPerfRepo.findBySummaryDate(target));
    }
}
