package com.mediq.controller;

import com.mediq.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mediq.service.OtpService;
import com.mediq.service.OtpVerificationResult;
import com.mediq.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final OtpService otpService;

    public UserController(UserService userService, OtpService otpService) {
        this.userService = userService;
        this.otpService = otpService;
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

    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        String currentUserId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("GET /users/{} by currentUserId={}", userId, currentUserId);
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.principal")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID userId) {
        String currentUserId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID requestedBy = UUID.fromString(currentUserId);
        return ResponseEntity.ok(userService.deactivateUser(userId, requestedBy));
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
        String adminIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID adminId = UUID.fromString(adminIdStr);
        return ResponseEntity.ok(userService.verifyDoctor(doctorUserId, request, adminId));
    }

    // ── OTP endpoints ─────────────────────────────────────────────────────────

    @PostMapping("/{userId}/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@PathVariable UUID userId) {
        otpService.sendOtp(userId);
        return ResponseEntity.ok(Map.of(
            "message", "OTP sent to your registered contact(s)",
            "expiresIn", "5 minutes"
        ));
    }

    @PostMapping("/{userId}/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> request) {
        String otp = request.get("otp");
        if (otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OTP is required"));
        }
        OtpVerificationResult result = otpService.verifyOtp(userId, otp);
        if (result.success()) {
            return ResponseEntity.ok(Map.of("message", result.message()));
        }
        return ResponseEntity.badRequest().body(Map.of(
            "status", result.status(),
            "message", result.message()
        ));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getMe(
            @RequestHeader("X-Keycloak-Id") String keycloakId) {
        return ResponseEntity.ok(userService.getUserByKeycloakId(keycloakId));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("user-service UP");
    }
}
