package com.mediq.notification.strategy.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("staticOtpSender")
public class StaticOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(StaticOtpSender.class);

    @Override
    public void sendOtp(String userId, String phone, String otp, int expiresIn) {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║           mediq OTP NOTIFICATION         ║");
        log.info("║  userId   : {}                           ", userId);
        log.info("║  phone    : {}                           ", phone);
        log.info("║  OTP      : {}                           ", otp);
        log.info("║  expires  : {} minutes                   ", expiresIn);
        log.info("╚══════════════════════════════════════════╝");
    }

    @Override
    public String strategyName() {
        return "STATIC_OTP_SENDER";
    }
}
