package com.mediq.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(EmailGateway.class);

    /**
     * Stub implementation — logs the email send request.
     * Replace with actual SMTP / SES / SendGrid integration.
     */
    public void send(String email, String subject, String body) {
        log.info("[EMAIL STUB] To={} | Subject={} | Body={}", email, subject, body);
    }
}
