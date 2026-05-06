package com.mediq.payment.service;

public record CreatePaymentIntentResponse(
    String paymentId,
    String clientSecret,
    String paymentIntentId
) {}
