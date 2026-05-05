package com.trucare.model;

/**
 * Java 17 FEATURE — Record as domain model.
 *
 * Same immutable record pattern as Patient in patient-service.
 * Consistency in domain modelling across services makes the codebase
 * easier to reason about — a key principle in microservices.
 */
public record Referral(
        String referralId,
        String patientId,
        String referredBy,      // doctor who raised the referral
        String referredTo,      // specialist or department
        String reason,
        ReferralStatus status
) {
    public Referral {
        if (referralId == null || referralId.isBlank())
            throw new IllegalArgumentException("Referral id must not be blank");
        if (patientId == null || patientId.isBlank())
            throw new IllegalArgumentException("Patient id must not be blank");
    }
}
