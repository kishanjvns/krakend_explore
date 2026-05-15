package com.mediq.controller;

import com.mediq.dto.*;
import com.mediq.model.UserType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mediq.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── Public endpoints (no auth required in KrakenD) ────────────────────────

    @PostMapping("/patients/register")
    public ResponseEntity<UserResponse> registerPatient(
            @Valid @RequestBody RegisterPatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.registerPatient(request));
    }

    @PostMapping("/doctors/register")
    public ResponseEntity<UserResponse> registerDoctor(
            @Valid @RequestBody RegisterDoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.registerDoctor(request));
    }

    // ── Protected endpoints (JWT required via KrakenD) ────────────────────────

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getMe() {
        String userId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.getUserById(UUID.fromString(userId)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.principal")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID userId) {
        String currentUserId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.deactivateUser(userId,
            UUID.fromString(currentUserId)));
    }

    // ── Admin-only endpoints ──────────────────────────────────────────────────

    @GetMapping("/doctors/pending-verification")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getPendingVerifications() {
        return ResponseEntity.ok(userService.getPendingDoctorVerifications());
    }

    @PutMapping("/doctors/{doctorUserId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> verifyDoctor(
            @PathVariable UUID doctorUserId,
            @Valid @RequestBody DoctorVerificationRequest request) {
        String adminId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.verifyDoctor(doctorUserId, request,
            UUID.fromString(adminId)));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("user-service UP");
    }
}
