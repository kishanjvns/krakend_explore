package com.trucare.controller;

import com.trucare.model.ReferralResponse;
import com.trucare.service.ReferralService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller — HTTP entry point for referral-service.
 *
 * Spring Boot CONCEPT — @RestController
 *   = @Controller + @ResponseBody
 *   Every return value is auto-serialised to JSON.
 *
 * URL mapping strategy:
 *   Internal (KrakenD calls)  : /referrals
 *   External (client calls)   : /api/v1/referrals  ← defined in krakend.json
 *
 * IMPORTANT — Spring MVC path specificity rules:
 *   When multiple mappings share the same base path, Spring resolves them
 *   by specificity — literal segments beat wildcard segments.
 *   So /referrals/open beats /referrals/{referralId} because "open" is
 *   a literal, not a variable. Declare order does NOT matter here.
 *
 *   However, /referrals/patient/{patientId} and /referrals/{referralId}
 *   are BOTH one wildcard segment — Spring picks the most specific based
 *   on the full path depth. Since "patient" is a literal prefix, Spring
 *   correctly routes /referrals/patient/P001 to getReferralsByPatientId.
 *
 * Interview note:
 *   Understanding Spring MVC path specificity matters in interviews.
 *   The rule: more literal characters = more specific = higher priority.
 */
@RestController
@RequestMapping("/referrals")
public class ReferralController {

    private final ReferralService referralService;

    /** Constructor injection — preferred over @Autowired field injection. */
    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    /**
     * GET /referrals
     * KrakenD exposes as: GET /api/v1/referrals
     */
    @GetMapping
    public ResponseEntity<List<ReferralResponse>> getAllReferrals() {
        return ResponseEntity.ok(referralService.getAllReferrals());
    }

    /**
     * GET /referrals/open
     * KrakenD exposes as: GET /api/v1/referrals/open
     *
     * Literal path segment — Spring MVC resolves this BEFORE /{referralId}
     * even without explicit ordering.
     */
    @GetMapping("/open")
    public ResponseEntity<List<ReferralResponse>> getOpenReferrals() {
        return ResponseEntity.ok(referralService.getOpenReferrals());
    }

    /**
     * GET /referrals/status/{status}
     * KrakenD exposes as: GET /api/v1/referrals/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ReferralResponse>> getReferralsByStatus(
            @PathVariable String status) {
        return ResponseEntity.ok(referralService.getReferralsByStatus(status));
    }

    /**
     * GET /referrals/patient/{patientId}
     * KrakenD exposes as: GET /api/v1/referrals/patient/{patientId}
     *
     * Returns all referrals for a given patient.
     * This endpoint is KEY for Step 3 aggregation — KrakenD will call BOTH
     * /patients/{id} and /referrals/patient/{id} in parallel and merge them
     * into a single patient-summary response.
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<ReferralResponse>> getReferralsByPatientId(
            @PathVariable String patientId) {
        return ResponseEntity.ok(referralService.getReferralsByPatientId(patientId));
    }

    /**
     * GET /referrals/{referralId}
     * KrakenD exposes as: GET /api/v1/referrals/{referralId}
     *
     * Single-segment wildcard — resolved after all literal paths above.
     */
    @GetMapping("/{referralId}")
    public ResponseEntity<ReferralResponse> getReferralById(
            @PathVariable String referralId) {
        return ResponseEntity.ok(referralService.getReferralById(referralId));
    }

    /**
     * GET /referrals/health
     * Used by: Docker healthcheck, ALB target group, KrakenD backend probes.
     *
     * Java 9+: Map.of() — unmodifiable map, concise syntax.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "referral-service",
                "version", "1.0.0"
        ));
    }
}
