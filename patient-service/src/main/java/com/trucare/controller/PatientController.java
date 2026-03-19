package com.trucare.controller;

import com.trucare.model.PatientResponse;
import com.trucare.service.PatientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller — HTTP entry point for patient-service.
 *
 * Spring Boot CONCEPT — @RestController
 *   = @Controller + @ResponseBody
 *   Every method return value is automatically serialised to JSON.
 *   No need for @ResponseBody on each method individually.
 *
 * Spring Boot EVOLUTION:
 *   Spring 3.x : @Controller + @ResponseBody required per method
 *   Spring 4.x : @RestController introduced as a convenience
 *
 * URL mapping strategy (separation of concerns):
 *   Internal path (what KrakenD calls)  : /patients
 *   External path (what clients call)   : /api/v1/patients   ← set in krakend.json
 *
 * This means you can version the public API in KrakenD config
 * without changing any service code — clean contract separation.
 *
 * Controller design rules:
 *   1. Deserialise HTTP input (@PathVariable, @RequestParam, @RequestBody)
 *   2. Delegate to service layer — NO business logic here
 *   3. Wrap result in ResponseEntity with correct HTTP status
 *   All exception handling lives in GlobalExceptionHandler, not here.
 *
 * Interview note:
 *   Constructor injection is preferred over @Autowired field injection because:
 *     - Works with final fields (immutability guarantee)
 *     - Makes dependencies explicit (easier unit testing with mocks)
 *     - Fails at application startup if bean is missing (fail-fast)
 *     - In Spring Boot 3.x, single-constructor classes don't need @Autowired
 */
@RestController
@RequestMapping("/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /**
     * GET /patients
     * KrakenD exposes as: GET /api/v1/patients
     */
    @GetMapping
    public ResponseEntity<List<PatientResponse>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    /**
     * GET /patients/{id}
     * KrakenD exposes as: GET /api/v1/patients/{id}
     *
     * @PathVariable extracts {id} from the URL path segment.
     * If patient not found, PatientNotFoundException is thrown in the service
     * and mapped to HTTP 404 by GlobalExceptionHandler.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getPatientById(@PathVariable String id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    /**
     * GET /patients/status/{status}
     * KrakenD exposes as: GET /api/v1/patients/status/{status}
     *
     * Spring MVC path matching:
     *   /patients/status/{status} is more specific than /patients/{id}
     *   because "status" is a literal segment. Spring resolves correctly
     *   regardless of declaration order — most specific match wins.
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PatientResponse>> getPatientsByStatus(
            @PathVariable String status) {
        return ResponseEntity.ok(patientService.getPatientsByStatus(status));
    }

    /**
     * GET /patients/active
     * KrakenD exposes as: GET /api/v1/patients/active
     *
     * Returns admitted + outpatient + critical patients only.
     */
    @GetMapping("/active")
    public ResponseEntity<List<PatientResponse>> getActivePatients() {
        return ResponseEntity.ok(patientService.getActivePatients());
    }

    /**
     * GET /patients/health
     * Used by: Docker healthcheck, Spring Actuator, AWS ALB target group probes.
     *
     * Java 9+: Map.of() creates an UNMODIFIABLE map.
     * Java 8 equivalent:
     *   Map<String, String> m = new LinkedHashMap<>();
     *   m.put("status", "UP");
     *   m.put("service", "patient-service");
     *   return Collections.unmodifiableMap(m);
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
