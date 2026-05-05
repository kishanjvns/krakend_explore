package com.trucare.exception;

public class ReferralNotFoundException extends RuntimeException {

    private final String referralId;

    public ReferralNotFoundException(String referralId) {
        super("Referral not found with id: " + referralId);
        this.referralId = referralId;
    }

    public String getReferralId() {
        return referralId;
    }
}
