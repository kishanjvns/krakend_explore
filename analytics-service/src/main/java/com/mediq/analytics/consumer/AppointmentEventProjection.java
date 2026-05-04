package com.mediq.analytics.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediq.analytics.model.DailyAppointmentSummaryEntity;
import com.mediq.analytics.model.DoctorPerformanceSummaryEntity;
import com.mediq.analytics.repository.DailyAppointmentSummaryRepository;
import com.mediq.analytics.repository.DoctorPerformanceSummaryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class AppointmentEventProjection {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventProjection.class);

    private final DailyAppointmentSummaryRepository dailyRepo;
    private final DoctorPerformanceSummaryRepository doctorPerfRepo;
    private final ObjectMapper objectMapper;

    public AppointmentEventProjection(DailyAppointmentSummaryRepository dailyRepo,
                                       DoctorPerformanceSummaryRepository doctorPerfRepo,
                                       ObjectMapper objectMapper) {
        this.dailyRepo = dailyRepo;
        this.doctorPerfRepo = doctorPerfRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "mediq.appointment.events", groupId = "mediq-analytics-appointment-group")
    @Transactional
    public void onAppointmentEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            String eventType = event.path("eventType").asText();
            String doctorId = event.path("doctorId").asText(null);
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            DailyAppointmentSummaryEntity daily = dailyRepo.findBySummaryDate(today)
                    .orElseGet(() -> dailyRepo.save(new DailyAppointmentSummaryEntity(today)));

            switch (eventType) {
                case "AppointmentBooked" -> {
                    daily.setTotalBooked(daily.getTotalBooked() + 1);
                    incrementDoctorPerf(doctorId, today, "booked");
                }
                case "AppointmentConfirmed" -> {
                    daily.setTotalConfirmed(daily.getTotalConfirmed() + 1);
                    incrementDoctorPerf(doctorId, today, "completed");
                }
                case "AppointmentCancelled" -> {
                    daily.setTotalCancelled(daily.getTotalCancelled() + 1);
                    incrementDoctorPerf(doctorId, today, "cancelled");
                }
                case "AppointmentCompleted" -> {
                    daily.setTotalCompleted(daily.getTotalCompleted() + 1);
                }
            }
            dailyRepo.save(daily);
        } catch (Exception ex) {
            log.warn("Failed to project appointment event offset={}: {}", record.offset(), ex.getMessage());
        }
    }

    private void incrementDoctorPerf(String doctorId, LocalDate date, String field) {
        if (doctorId == null || doctorId.isBlank()) return;
        DoctorPerformanceSummaryEntity perf = doctorPerfRepo
                .findById(new DoctorPerformanceSummaryEntity.DoctorDateId(doctorId, date))
                .orElseGet(() -> doctorPerfRepo.save(new DoctorPerformanceSummaryEntity(doctorId, date)));
        switch (field) {
            case "booked" -> perf.setTotalAppointments(perf.getTotalAppointments() + 1);
            case "completed" -> perf.setCompletedAppointments(perf.getCompletedAppointments() + 1);
            case "cancelled" -> perf.setCancelledAppointments(perf.getCancelledAppointments() + 1);
        }
        doctorPerfRepo.save(perf);
    }
}
