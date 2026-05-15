package com.mediq.notification.service;

public record OtpVerificationResult(boolean success, String status, String message) {

    public static OtpVerificationResult successResult() {
        return new OtpVerificationResult(true, "SUCCESS", "OTP verified successfully.");
    }

    public static OtpVerificationResult wrong(String message) {
        return new OtpVerificationResult(false, "WRONG_OTP", message);
    }

    public static OtpVerificationResult expired(String message) {
        return new OtpVerificationResult(false, "EXPIRED", message);
    }

    public static OtpVerificationResult invalidated(String message) {
        return new OtpVerificationResult(false, "INVALIDATED", message);
    }
}
