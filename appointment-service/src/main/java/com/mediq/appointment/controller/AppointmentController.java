package com.mediq.appointment.controller;

import com.mediq.appointment.dto.AppointmentResponse;
import com.mediq.appointment.dto.BookAppointmentRequest;
import com.mediq.appointment.dto.CreateSlotRequest;
import com.mediq.appointment.dto.SlotResponse;
import com.mediq.appointment.model.AppointmentSlotEntity;
import com.mediq.appointment.repository.AppointmentSlotRepository;
import com.mediq.appointment.service.AppointmentService;
import com.mediq.appointment.temporal.workflow.AppointmentBookingWorkflow;
import com.mediq.appointment.temporal.workflow.BookingRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentSlotRepository slotRepository;
    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public AppointmentController(
            AppointmentService appointmentService,
            AppointmentSlotRepository slotRepository,
            WorkflowClient workflowClient,
            @Value("${temporal.task-queue}") String taskQueue) {
        this.appointmentService = appointmentService;
        this.slotRepository = slotRepository;
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasAuthority('WRITE_OWN_APPOINTMENT') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> bookAppointment(
            @RequestBody BookAppointmentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {

        UUID patientId = request.patientId() != null
            ? request.patientId()
            : (userId != null ? UUID.fromString(userId) : null);

        UUID doctorId = request.doctorId();
        if (doctorId == null && request.slotId() != null) {
            doctorId = slotRepository.findById(request.slotId())
                .map(AppointmentSlotEntity::getDoctorId)
                .orElse(null);
        }

        BookingRequest bookingRequest = new BookingRequest(
            patientId,
            doctorId,
            request.slotId(),
            request.amount() != null ? request.amount() : BigDecimal.ZERO,
            userEmail != null ? userEmail : ""
        );

        String workflowId = "appointment-" + UUID.randomUUID();

        AppointmentBookingWorkflow workflow = workflowClient.newWorkflowStub(
            AppointmentBookingWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(15))
                .build());

        WorkflowClient.start(workflow::bookAppointment, bookingRequest);

        return ResponseEntity.accepted().body(Map.of(
            "workflowId", workflowId,
            "status", "INITIATED",
            "message", "Booking started, awaiting payment"
        ));
    }

    @GetMapping("/appointments/{workflowId}/status")
    @PreAuthorize("hasAuthority('READ_OWN_APPOINTMENT') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getBookingStatus(
            @PathVariable String workflowId) {
        AppointmentBookingWorkflow workflow = workflowClient.newWorkflowStub(
            AppointmentBookingWorkflow.class, workflowId);
        return ResponseEntity.ok(Map.of(
            "workflowId", workflowId,
            "status", workflow.getBookingStatus()
        ));
    }

    @GetMapping("/appointments/{appointmentId}")
    @PreAuthorize("hasAuthority('READ_OWN_APPOINTMENT') or hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponse> getAppointment(
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.getAppointment(appointmentId));
    }

    @PutMapping("/appointments/{appointmentId}/confirm")
    @PreAuthorize("hasAuthority('CONFIRM_APPOINTMENT') or hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponse> confirmAppointment(
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.confirmAppointment(appointmentId));
    }

    @PutMapping("/appointments/{appointmentId}/cancel")
    @PreAuthorize("hasAuthority('CANCEL_OWN_APPOINTMENT') or hasAuthority('CANCEL_APPOINTMENT') or hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @PathVariable UUID appointmentId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(appointmentService.cancelAppointment(appointmentId, reason));
    }

    @PostMapping("/slots")
    @PreAuthorize("hasAuthority('WRITE_APPOINTMENT_SLOT') or hasRole('ADMIN')")
    public ResponseEntity<SlotResponse> createSlot(@RequestBody CreateSlotRequest request) {
        return ResponseEntity.ok(appointmentService.createSlot(request));
    }

    @GetMapping("/slots")
    @PreAuthorize("hasAuthority('READ_OWN_APPOINTMENT') or hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentSlotEntity>> getSlots(
            @RequestParam UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(slotRepository.findByDoctorIdAndSlotDate(doctorId, date));
    }
}
