package com.mediq.controller;

import com.mediq.dto.*;
import com.mediq.interceptor.UserContextHolder;
import com.mediq.model.UserContext;
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
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /users/{} by userId={} role={}", userId, ctx.userId(), ctx.role());
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID userId) {
        UserContext ctx = UserContextHolder.get();
        UUID requestedBy = UUID.fromString(ctx.userId());
        return ResponseEntity.ok(userService.deactivateUser(userId, requestedBy));
    }

    // ── Admin-only endpoints ──────────────────────────────────────────────────

    @GetMapping("/doctors/pending-verification")
    public ResponseEntity<List<UserResponse>> getPendingVerifications() {
        UserContext ctx = UserContextHolder.get();
        if (!"ADMIN".equals(ctx.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.getPendingDoctorVerifications());
    }

    @PutMapping("/doctors/{doctorUserId}/verify")
    public ResponseEntity<UserResponse> verifyDoctor(
            @PathVariable UUID doctorUserId,
            @Valid @RequestBody DoctorVerificationRequest request) {
        UserContext ctx = UserContextHolder.get();
        if (!"ADMIN".equals(ctx.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UUID adminId = UUID.fromString(ctx.userId());
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

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("user-service UP");
    }
}
