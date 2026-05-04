package com.mediq.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SmsGateway.class);

    /**
     * Stub implementation — logs the SMS send request.
     * Replace with actual Twilio / SNS / etc. integration.
     */
    public void send(String phone, String message) {
        log.info("[SMS STUB] To={} | Message={}", phone, message);
    }
}
