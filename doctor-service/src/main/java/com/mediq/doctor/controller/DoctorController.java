package com.mediq.doctor.controller;

import com.mediq.doctor.dto.AddSpecializationRequest;
import com.mediq.doctor.dto.DoctorProfileResponse;
import com.mediq.doctor.dto.DoctorSearchResponse;
import com.mediq.doctor.dto.SetAvailabilityRequest;
import com.mediq.doctor.model.DoctorAvailabilityEntity;
import com.mediq.doctor.repository.DoctorAvailabilityRepository;
import com.mediq.doctor.service.DoctorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/doctors")
public class DoctorController {

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
        return ResponseEntity.ok(doctorService.searchDoctors(specialization, verified));
    }

    @GetMapping("/{doctorId}")
    public ResponseEntity<DoctorProfileResponse> getDoctorProfile(@PathVariable UUID doctorId) {
        return ResponseEntity.ok(doctorService.getDoctorProfile(doctorId));
    }

    @PostMapping("/{doctorId}/specializations")
    public ResponseEntity<Void> addSpecialization(@PathVariable UUID doctorId,
                                                   @RequestBody AddSpecializationRequest request) {
        doctorService.addSpecialization(doctorId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{doctorId}/availability")
    public ResponseEntity<Void> setAvailability(@PathVariable UUID doctorId,
                                                 @RequestBody SetAvailabilityRequest request) {
        doctorService.setAvailability(doctorId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{doctorId}/availability")
    public ResponseEntity<List<DoctorAvailabilityEntity>> getAvailability(@PathVariable UUID doctorId) {
        return ResponseEntity.ok(availabilityRepository.findByDoctorIdAndActive(doctorId, true));
    }
}
