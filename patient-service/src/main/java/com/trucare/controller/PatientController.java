package com.trucare.controller;

import com.trucare.interceptor.UserContextHolder;
import com.trucare.model.PatientResponse;
import com.trucare.model.UserContext;
import com.trucare.service.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Updated PatientController — Step 4: JWT Claims Awareness
 *
 * Controllers remain thin — no auth logic here.
 * Auth enforcement is KrakenD's job.
 * This controller only READS the identity that KrakenD already validated
 * and forwards as headers.
 *
 * Key pattern:
 *   KrakenD validates JWT → strips raw token → adds X-User-* headers
 *   JwtClaimsInterceptor reads those headers → populates UserContextHolder
 *   Controller reads UserContextHolder → knows who the caller is
 *
 * This pattern gives you:
 *   - Audit logging (who accessed which patient record)
 *   - Role-based response shaping (doctors see more fields than nurses)
 *   - Future: row-level security (doctors only see their own patients)
 */
@RestController
@RequestMapping("/patients")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /**
     * GET /patients
     * KrakenD exposes as: GET /api/v1/patients
     *
     * Public endpoint — no JWT required in krakend.json.
     * UserContext will be anonymous if no auth header is present.
     */
    @GetMapping
    public ResponseEntity<List<PatientResponse>> getAllPatients() {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /patients called by userId={} role={}", ctx.userId(), ctx.role());
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    /**
     * GET /patients/{id}
     * KrakenD exposes as: GET /api/v1/patients/{id}
     *
     * Protected endpoint — JWT required in krakend.json.
     * UserContext will always have a real userId here.
     *
     * Demonstrates:
     *   1. Audit log — who accessed which patient
     *   2. Role-based response (doctors get full detail, others get summary)
     */
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getPatientById(@PathVariable String id) {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /patients/{} accessed by userId={} role={}", id, ctx.userId(), ctx.role());

        PatientResponse patient = patientService.getPatientById(id);

        /*
         * Role-based shaping example:
         * In a real system, doctors might see diagnosis details while
         * nurses see only basic patient info. Here we log the distinction.
         * Actual field-level filtering can be done via KrakenD `allow` (Step 6)
         * or by the service returning different DTOs per role.
         */
        if (ctx.isDoctor() || ctx.isAdmin()) {
            log.debug("Full patient detail returned to role={}", ctx.role());
        } else {
            log.debug("Standard patient detail returned to role={}", ctx.role());
        }

        return ResponseEntity.ok(patient);
    }

    /**
     * GET /patients/status/{status}
     * KrakenD exposes as: GET /api/v1/patients/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PatientResponse>> getPatientsByStatus(
            @PathVariable String status) {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /patients/status/{} by userId={}", status, ctx.userId());
        return ResponseEntity.ok(patientService.getPatientsByStatus(status));
    }

    /**
     * GET /patients/active
     * KrakenD exposes as: GET /api/v1/patients/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<PatientResponse>> getActivePatients() {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /patients/active by userId={}", ctx.userId());
        return ResponseEntity.ok(patientService.getActivePatients());
    }

    /**
     * GET /patients/health
     * Public — excluded from interceptor path patterns.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "patient-service",
                "version", "1.0.0"
        ));
    }
}
