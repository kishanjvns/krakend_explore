package com.trucare.model;

/**
 * Response DTO — separates domain model from API contract.
 *
 * Note the serviceSource field: when KrakenD aggregates responses
 * from patient-service AND referral-service in Step 3, both will
 * appear in the merged JSON. The serviceSource field tells you
 * which backend produced which part of the response — invaluable
 * for debugging in a distributed system.
 */
public record ReferralResponse(
        String referralId,
        String patientId,
        String referredBy,
        String referredTo,
        String reason,
        String status,
        boolean isOpen,
        String serviceSource
) {
    public static ReferralResponse from(Referral referral) {
        return new ReferralResponse(
                referral.referralId(),
                referral.patientId(),
                referral.referredBy(),
                referral.referredTo(),
                referral.reason(),
                referral.status().value(),
                referral.status().isOpen(),
                "referral-service"
        );
    }
}
