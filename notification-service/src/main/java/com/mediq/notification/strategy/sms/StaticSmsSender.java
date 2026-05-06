package com.mediq.notification.strategy.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("staticSmsSender")
public class StaticSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(StaticSmsSender.class);

    @Override
    public void sendSms(String phone, String message) {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║           mediq SMS NOTIFICATION         ║");
        log.info("║  to      : {}                            ", phone);
        log.info("║  message : {}                            ", message);
        log.info("╚══════════════════════════════════════════╝");
    }

    @Override
    public String strategyName() {
        return "STATIC_SMS_SENDER";
    }
}
