package com.mediq.doctor.controller;

import com.mediq.doctor.dto.AddSpecializationRequest;
import com.mediq.doctor.dto.DoctorProfileResponse;
import com.mediq.doctor.dto.DoctorSearchResponse;
import com.mediq.doctor.dto.SetAvailabilityRequest;
import com.mediq.doctor.model.DoctorAvailabilityEntity;
import com.mediq.doctor.repository.DoctorAvailabilityRepository;
import com.mediq.doctor.service.DoctorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/doctors")
public class DoctorController {

    private static final Logger log = LoggerFactory.getLogger(DoctorController.class);

    private final DoctorService doctorService;
    private final DoctorAvailabilityRepository availabilityRepository;

    public DoctorController(DoctorService doctorService,
                            DoctorAvailabilityRepository availabilityRepository) {
        this.doctorService = doctorService;
        this.availabilityRepository = availabilityRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<List<DoctorSearchResponse>> searchDoctors(
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) Boolean verified) {
        log.debug("GET /doctors/search specialization={} verified={}", specialization, verified);
        List<DoctorSearchResponse> results = doctorService.searchDoctors(specialization, verified);
        log.debug("GET /doctors/search returning {} results", results.size());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{doctorId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN') or hasRole('NURSE')")
    public ResponseEntity<DoctorProfileResponse> getDoctorProfile(@PathVariable UUID doctorId) {
        log.debug("GET /doctors/{}", doctorId);
        return ResponseEntity.ok(doctorService.getDoctorProfile(doctorId));
    }

    @PostMapping("/{doctorId}/specializations")
    @PreAuthorize("(hasRole('DOCTOR') and #doctorId.toString() == authentication.principal) or hasRole('ADMIN')")
    public ResponseEntity<Void> addSpecialization(@PathVariable UUID doctorId,
                                                   @RequestBody AddSpecializationRequest request) {
        log.info("POST /doctors/{}/specializations name={}", doctorId, request.specialization());
        doctorService.addSpecialization(doctorId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{doctorId}/availability")
    @PreAuthorize("(hasRole('DOCTOR') and #doctorId.toString() == authentication.principal) or hasRole('ADMIN')")
    public ResponseEntity<Void> setAvailability(@PathVariable UUID doctorId,
                                                 @RequestBody SetAvailabilityRequest request) {
        log.info("POST /doctors/{}/availability", doctorId);
        doctorService.setAvailability(doctorId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{doctorId}/availability")
    public ResponseEntity<List<DoctorAvailabilityEntity>> getAvailability(@PathVariable UUID doctorId) {
        log.debug("GET /doctors/{}/availability", doctorId);
        return ResponseEntity.ok(availabilityRepository.findByDoctorIdAndActive(doctorId, true));
    }
}
