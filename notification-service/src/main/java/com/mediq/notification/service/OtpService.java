package com.mediq.notification.service;

import com.mediq.notification.event.OtpRequestedEvent;
import com.mediq.notification.event.OtpVerifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String OTP_KEY = "otp:";
    private static final String ATTEMPTS_KEY = "otp:attempts:";
    private static final int OTP_TTL_MIN = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String otpEventsTopic;

    public OtpService(
            StringRedisTemplate redisTemplate,
            NotificationService notificationService,
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${mediq.kafka.topic.otp-events}") String otpEventsTopic) {
        this.redisTemplate = redisTemplate;
        this.notificationService = notificationService;
        this.kafkaTemplate = kafkaTemplate;
        this.otpEventsTopic = otpEventsTopic;
    }

    public void generateAndSend(OtpRequestedEvent event) {
        String otp = generateOtp();
        String otpKey = OTP_KEY + event.userId();
        String attemptsKey = ATTEMPTS_KEY + event.userId();

        redisTemplate.opsForValue().set(otpKey, otp, Duration.ofMinutes(OTP_TTL_MIN));
        redisTemplate.delete(attemptsKey);

        log.info("OTP generated and stored → userId={} contactType={}", event.userId(), event.contactType());

        if ("EMAIL".equals(event.contactType())) {
            notificationService.sendOtpViaEmail(
                event.userId(), event.destination(), event.userName(), otp, OTP_TTL_MIN);
        } else if ("PHONE".equals(event.contactType())) {
            notificationService.sendOtpViaPhone(
                event.userId(), event.destination(), otp, OTP_TTL_MIN);
        }
    }

    public void sendOtp(String userId, String contactType, String destination, String userName) {
        String otp = generateOtp();
        String otpKey = OTP_KEY + userId;
        String attemptsKey = ATTEMPTS_KEY + userId;

        redisTemplate.opsForValue().set(otpKey, otp, Duration.ofMinutes(OTP_TTL_MIN));
        redisTemplate.delete(attemptsKey);

        log.info("OTP resend → userId={} contactType={}", userId, contactType);

        if ("EMAIL".equalsIgnoreCase(contactType)) {
            notificationService.sendOtpViaEmail(userId, destination, userName, otp, OTP_TTL_MIN);
        } else if ("PHONE".equalsIgnoreCase(contactType)) {
            notificationService.sendOtpViaPhone(userId, destination, otp, OTP_TTL_MIN);
        }
    }

    public OtpVerificationResult verifyOtp(String userId, String submittedOtp) {
        String otpKey = OTP_KEY + userId;
        String attemptsKey = ATTEMPTS_KEY + userId;

        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            return OtpVerificationResult.expired("OTP expired or not found. Please request a new OTP.");
        }

        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(otpKey);
            return OtpVerificationResult.invalidated("Too many wrong attempts. Please request a new OTP.");
        }

        if (!storedOtp.equals(submittedOtp)) {
            int newAttempts = attempts + 1;
            redisTemplate.opsForValue()
                .set(attemptsKey, String.valueOf(newAttempts), Duration.ofMinutes(OTP_TTL_MIN));

            if (newAttempts >= MAX_ATTEMPTS) {
                redisTemplate.delete(otpKey);
                return OtpVerificationResult.invalidated(
                    "Too many wrong attempts. OTP invalidated. Please request a new OTP.");
            }

            return OtpVerificationResult.wrong(
                "Invalid OTP. " + (MAX_ATTEMPTS - newAttempts) + " attempts remaining.");
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);

        OtpVerifiedEvent verifiedEvent = OtpVerifiedEvent.of(userId);
        kafkaTemplate.send(otpEventsTopic, userId, verifiedEvent);
        log.info("OTP_VERIFIED published → userId={}", userId);

        return OtpVerificationResult.successResult();
    }

    private String generateOtp() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}
