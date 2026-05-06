package com.mediq.notification.strategy.otp;

public interface OtpSender {
    void sendOtp(String userId, String phone, String otp, int expiresIn);
    String strategyName();
}
