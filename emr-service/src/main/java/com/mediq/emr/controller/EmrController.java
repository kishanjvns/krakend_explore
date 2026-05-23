package com.mediq.emr.controller;

import com.mediq.emr.dto.RecordEmrEventRequest;
import com.mediq.emr.model.EmrEventType;
import com.mediq.emr.model.PatientEventEntity;
import com.mediq.emr.model.PatientSummaryEntity;
import com.mediq.emr.service.EmrService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/emr/patients/{patientId}")
public class EmrController {

    private static final Logger log = LoggerFactory.getLogger(EmrController.class);

    private final EmrService emrService;

    public EmrController(EmrService emrService) {
        this.emrService = emrService;
    }

    @PostMapping("/events/{eventType}")
    @PreAuthorize("hasAuthority('WRITE_EMR')")
    public ResponseEntity<PatientEventEntity> recordEvent(
            @PathVariable String patientId,
            @PathVariable EmrEventType eventType,
            @Valid @RequestBody RecordEmrEventRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        String recorder = request.recordedBy() != null ? request.recordedBy() : userId;
        log.info("POST /emr/patients/{}/events/{} recorder={}", patientId, eventType, recorder);
        PatientEventEntity saved = emrService.appendEvent(patientId, eventType, request.payload(), recorder);
        log.info("EMR event recorded patientId={} eventType={} eventId={}", patientId, eventType, saved.getId());
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('READ_EMR')")
    public ResponseEntity<PatientSummaryEntity> getCurrentState(@PathVariable String patientId) {
        log.debug("GET /emr/patients/{}/current", patientId);
        return ResponseEntity.ok(emrService.getCurrentState(patientId));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('READ_EMR')")
    public ResponseEntity<List<PatientEventEntity>> getHistory(@PathVariable String patientId) {
        log.debug("GET /emr/patients/{}/history", patientId);
        List<PatientEventEntity> events = emrService.getHistory(patientId);
        log.debug("GET /emr/patients/{}/history returning {} events", patientId, events.size());
        return ResponseEntity.ok(events);
    }

    @GetMapping("/as-of")
    @PreAuthorize("hasAuthority('READ_EMR')")
    public ResponseEntity<PatientSummaryEntity> getStateAsOf(
            @PathVariable String patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /emr/patients/{}/as-of date={}", patientId, date);
        return ResponseEntity.ok(emrService.replayToDate(patientId, date));
    }
}
