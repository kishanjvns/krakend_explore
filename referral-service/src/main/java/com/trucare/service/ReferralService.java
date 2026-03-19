package com.trucare.service;

import com.trucare.exception.ReferralNotFoundException;
import com.trucare.model.Referral;
import com.trucare.model.ReferralResponse;
import com.trucare.model.ReferralStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer — owns all referral business logic.
 *
 * Spring Boot CONCEPT — @Service
 *   Stereotype annotation for the business/domain layer.
 *   Scanned by @ComponentScan from the main application class.
 *
 * Java 17 features used:
 *   - List.of()       : unmodifiable in-memory store (Java 9+)
 *   - Stream.toList() : unmodifiable result list (Java 16+)
 *   - Method refs     : ReferralResponse::from
 *   - orElseThrow()   : Optional fail-fast with domain exception
 *   - Switch expression inside ReferralStatus.from()
 */
@Service
public class ReferralService {

    /**
     * In-memory store — simulates a real DB for Step 2 learning.
     * Java 9+: List.of() is unmodifiable — no accidental mutation.
     */
    private final List<Referral> referrals = List.of(
            new Referral("R001", "P001",
                    "Dr. Mehta",  "Cardiology",
                    "High BP follow-up",    ReferralStatus.from("approved")),
            new Referral("R002", "P002",
                    "Dr. Singh",  "Endocrinology",
                    "Diabetes management",  ReferralStatus.from("pending")),
            new Referral("R003", "P003",
                    "Dr. Kapoor", "ICU",
                    "Cardiac emergency",    ReferralStatus.from("completed")),
            new Referral("R004", "P001",
                    "Dr. Mehta",  "Neurology",
                    "Recurring headaches",  ReferralStatus.from("pending")),
            new Referral("R005", "P004",
                    "Dr. Joshi",  "Orthopaedics",
                    "Post-fracture review", ReferralStatus.from("rejected"))
    );

    /**
     * Returns all referrals as response DTOs.
     * Java 16+: Stream.toList() — replaces collect(Collectors.toUnmodifiableList())
     */
    public List<ReferralResponse> getAllReferrals() {
        return referrals.stream()
                .map(ReferralResponse::from)
                .toList();
    }

    /**
     * Returns single referral by ID or throws ReferralNotFoundException → HTTP 404.
     *
     * Interview note:
     *   orElseThrow() with a lambda supplier is the idiomatic Java 8+ pattern.
     *   Never use Optional.get() without isPresent() — it throws
     *   NoSuchElementException with no context, making prod debugging painful.
     */
    public ReferralResponse getReferralById(String referralId) {
        return referrals.stream()
                .filter(r -> r.referralId().equalsIgnoreCase(referralId))
                .map(ReferralResponse::from)
                .findFirst()
                .orElseThrow(() -> new ReferralNotFoundException(referralId));
    }

    /**
     * Returns all referrals for a given patient ID.
     * Returns an empty list (not 404) when the patient exists but has no referrals.
     * Throws 404 only when no referrals at all are found — clinical UX decision.
     */
    public List<ReferralResponse> getReferralsByPatientId(String patientId) {
        List<ReferralResponse> result = referrals.stream()
                .filter(r -> r.patientId().equalsIgnoreCase(patientId))
                .map(ReferralResponse::from)
                .toList();

        if (result.isEmpty()) {
            throw new ReferralNotFoundException(
                    "No referrals found for patient: " + patientId);
        }
        return result;
    }

    /**
     * Returns referrals filtered by status string.
     * ReferralStatus.from() validates early — throws IllegalArgumentException
     * (mapped to HTTP 400) for unknown status values.
     */
    public List<ReferralResponse> getReferralsByStatus(String status) {
        ReferralStatus target = ReferralStatus.from(status);
        return referrals.stream()
                .filter(r -> r.status().value().equalsIgnoreCase(target.value()))
                .map(ReferralResponse::from)
                .toList();
    }

    /**
     * Returns only open referrals (pending or approved).
     * Demonstrates the isOpen() method from the sealed ReferralStatus interface.
     */
    public List<ReferralResponse> getOpenReferrals() {
        return referrals.stream()
                .filter(r -> r.status().isOpen())
                .map(ReferralResponse::from)
                .toList();
    }
}
