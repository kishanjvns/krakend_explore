package com.mediq.service;

public record OtpVerificationResult(
        boolean success,
        String status,
        String message
) {
    public static OtpVerificationResult success() {
        return new OtpVerificationResult(true, "SUCCESS",
            "OTP verified successfully. Account activated.");
    }

    public static OtpVerificationResult wrong(String message) {
        return new OtpVerificationResult(false, "WRONG", message);
    }

    public static OtpVerificationResult expired(String message) {
        return new OtpVerificationResult(false, "EXPIRED", message);
    }

    public static OtpVerificationResult invalidated(String message) {
        return new OtpVerificationResult(false, "INVALIDATED", message);
    }
}
