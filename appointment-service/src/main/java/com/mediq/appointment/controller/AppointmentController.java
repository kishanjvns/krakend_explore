package com.mediq.appointment.controller;

import com.mediq.appointment.dto.AppointmentResponse;
import com.mediq.appointment.dto.BookAppointmentRequest;
import com.mediq.appointment.dto.CreateSlotRequest;
import com.mediq.appointment.dto.SlotResponse;
import com.mediq.appointment.model.AppointmentSlotEntity;
import com.mediq.appointment.repository.AppointmentSlotRepository;
import com.mediq.appointment.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentSlotRepository slotRepository;

    public AppointmentController(AppointmentService appointmentService,
                                  AppointmentSlotRepository slotRepository) {
        this.appointmentService = appointmentService;
        this.slotRepository = slotRepository;
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> bookAppointment(
            @RequestBody BookAppointmentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        BookAppointmentRequest effectiveRequest = request;
        if (userId != null && request.patientId() == null) {
            effectiveRequest = new BookAppointmentRequest(request.slotId(), UUID.fromString(userId));
        }
        return ResponseEntity.ok(appointmentService.bookAppointment(effectiveRequest));
    }

    @GetMapping("/appointments/{appointmentId}")
    public ResponseEntity<AppointmentResponse> getAppointment(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.getAppointment(appointmentId));
    }

    @PutMapping("/appointments/{appointmentId}/confirm")
    public ResponseEntity<AppointmentResponse> confirmAppointment(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.confirmAppointment(appointmentId));
    }

    @PutMapping("/appointments/{appointmentId}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @PathVariable UUID appointmentId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(appointmentService.cancelAppointment(appointmentId, reason));
    }

    @PostMapping("/slots")
    public ResponseEntity<SlotResponse> createSlot(@RequestBody CreateSlotRequest request) {
        return ResponseEntity.ok(appointmentService.createSlot(request));
    }

    @GetMapping("/slots")
    public ResponseEntity<List<AppointmentSlotEntity>> getSlots(
            @RequestParam UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(slotRepository.findByDoctorIdAndSlotDate(doctorId, date));
    }
}
