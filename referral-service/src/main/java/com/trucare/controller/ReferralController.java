package com.trucare.controller;

import com.trucare.interceptor.UserContextHolder;
import com.trucare.model.ReferralResponse;
import com.trucare.model.UserContext;
import com.trucare.service.ReferralService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Updated ReferralController — Step 4: JWT Claims Awareness
 *
 * Same UserContext pattern as PatientController.
 * In a real healthcare system, referral access is strictly role-based:
 *   - Doctors can view and create referrals for their own patients
 *   - Nurses can view referrals assigned to their ward
 *   - Admins can view all referrals
 *
 * Here we demonstrate the pattern with logging.
 * Step 6 (transformations) will show how KrakenD can filter response
 * fields based on the forwarded role claim.
 */
@RestController
@RequestMapping("/referrals")
public class ReferralController {

    private static final Logger log = LoggerFactory.getLogger(ReferralController.class);

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    /**
     * GET /referrals
     * KrakenD exposes as: GET /api/v1/referrals
     */
    @GetMapping
    public ResponseEntity<List<ReferralResponse>> getAllReferrals() {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /referrals by userId={} role={}", ctx.userId(), ctx.role());
        return ResponseEntity.ok(referralService.getAllReferrals());
    }

    /**
     * GET /referrals/open
     * KrakenD exposes as: GET /api/v1/referrals/open
     */
    @GetMapping("/open")
    public ResponseEntity<List<ReferralResponse>> getOpenReferrals() {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /referrals/open by userId={}", ctx.userId());
        return ResponseEntity.ok(referralService.getOpenReferrals());
    }

    /**
     * GET /referrals/status/{status}
     * KrakenD exposes as: GET /api/v1/referrals/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ReferralResponse>> getReferralsByStatus(
            @PathVariable String status) {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /referrals/status/{} by userId={}", status, ctx.userId());
        return ResponseEntity.ok(referralService.getReferralsByStatus(status));
    }

    /**
     * GET /referrals/patient/{patientId}
     * KrakenD exposes as: GET /api/v1/referrals/patient/{patientId}
     *
     * This endpoint is also called internally during aggregation
     * (GET /api/v1/patient-summary/{id} from Step 3).
     * In that case, KrakenD forwards the same claim headers it received
     * from the original client request.
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<ReferralResponse>> getReferralsByPatientId(
            @PathVariable String patientId) {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /referrals/patient/{} by userId={} role={}",
                patientId, ctx.userId(), ctx.role());

        /*
         * Future role-based access:
         * if (!ctx.isDoctor() && !ctx.isAdmin() && !ctx.isNurse()) {
         *     throw new AccessDeniedException("Insufficient role to view referrals");
         * }
         */

        return ResponseEntity.ok(referralService.getReferralsByPatientId(patientId));
    }

    /**
     * GET /referrals/{referralId}
     * KrakenD exposes as: GET /api/v1/referrals/{referralId}
     */
    @GetMapping("/{referralId}")
    public ResponseEntity<ReferralResponse> getReferralById(
            @PathVariable String referralId) {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /referrals/{} by userId={}", referralId, ctx.userId());
        return ResponseEntity.ok(referralService.getReferralById(referralId));
    }

    /**
     * GET /referrals/health
     * Public — excluded from interceptor.
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
