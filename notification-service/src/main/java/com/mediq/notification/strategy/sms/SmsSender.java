package com.mediq.notification.strategy.sms;

public interface SmsSender {
    void sendSms(String phone, String message);
    String strategyName();
}
