package com.mediq.notification.controller;

import com.mediq.notification.service.OtpService;
import com.mediq.notification.service.OtpVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
public class OtpController {

    private static final Logger log = LoggerFactory.getLogger(OtpController.class);

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/{userId}/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        String contactType = request.get("contactType");
        String destination = request.get("destination");
        String userName = request.getOrDefault("userName", "User");

        if (contactType == null || destination == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "contactType and destination are required"));
        }

        otpService.sendOtp(userId, contactType, destination, userName);

        return ResponseEntity.ok(Map.of(
            "message", "OTP sent to your registered contact",
            "expiresIn", "5 minutes"
        ));
    }

    @PostMapping("/{userId}/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @PathVariable String userId,
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
}
