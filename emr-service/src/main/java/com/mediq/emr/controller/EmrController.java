package com.mediq.emr.controller;

import com.mediq.emr.dto.RecordEmrEventRequest;
import com.mediq.emr.model.EmrEventType;
import com.mediq.emr.model.PatientEventEntity;
import com.mediq.emr.model.PatientSummaryEntity;
import com.mediq.emr.service.EmrService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/emr/patients/{patientId}")
public class EmrController {

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
        PatientEventEntity saved = emrService.appendEvent(patientId, eventType, request.payload(), recorder);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('READ_EMR')")
    public ResponseEntity<PatientSummaryEntity> getCurrentState(@PathVariable String patientId) {
        return ResponseEntity.ok(emrService.getCurrentState(patientId));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('READ_EMR')")
    public ResponseEntity<List<PatientEventEntity>> getHistory(@PathVariable String patientId) {
        return ResponseEntity.ok(emrService.getHistory(patientId));
    }

    @GetMapping("/as-of")
    @PreAuthorize("hasAuthority('READ_EMR')")
    public ResponseEntity<PatientSummaryEntity> getStateAsOf(
            @PathVariable String patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(emrService.replayToDate(patientId, date));
    }
}
