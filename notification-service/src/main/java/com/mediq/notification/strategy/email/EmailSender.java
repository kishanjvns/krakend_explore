package com.mediq.notification.strategy.email;

public interface EmailSender {
    void sendEmail(String to, String subject, String body, boolean isHtml);
    String strategyName();
}
