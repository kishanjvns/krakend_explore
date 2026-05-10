package com.mediq.service;

import com.mediq.event.OtpRequestedEvent;
import com.mediq.exception.UserNotFoundException;
import com.mediq.model.UserEntity;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String OTP_KEY      = "otp:";
    private static final String ATTEMPTS_KEY = "otp:attempts:";
    private static final int    OTP_TTL_MIN  = 5;
    private static final int    MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String userEventsTopic;

    public OtpService(
            UserRepository userRepository,
            StringRedisTemplate stringRedisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${mediq.kafka.topic.user-events}") String userEventsTopic) {
        this.userRepository = userRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.userEventsTopic = userEventsTopic;
    }

    @Transactional(readOnly = true)
    public void sendOtp(UUID userId) {
        UserEntity user = userRepository.findByIdWithDetails(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        String otp = generateOtp();
        String otpKey = OTP_KEY + userId;
        String attemptsKey = ATTEMPTS_KEY + userId;

        stringRedisTemplate.opsForValue().set(otpKey, otp, Duration.ofMinutes(OTP_TTL_MIN));
        stringRedisTemplate.delete(attemptsKey);

        String userName = user.getFirstName() + " " + user.getLastName();

        user.getContacts().forEach(contact -> {
            OtpRequestedEvent event = OtpRequestedEvent.of(
                userId.toString(),
                contact.getContactType().name(),
                contact.getContactValue(),
                userName,
                otp
            );
            kafkaTemplate.send(userEventsTopic, userId.toString(), event);
            log.info("OtpRequestedEvent published → userId={} contactType={}",
                userId, contact.getContactType());
        });
    }

    @Transactional
    public OtpVerificationResult verifyOtp(UUID userId, String submittedOtp) {
        String otpKey      = OTP_KEY + userId;
        String attemptsKey = ATTEMPTS_KEY + userId;

        String storedOtp = stringRedisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            return OtpVerificationResult.expired(
                "OTP expired or not found. Please request a new OTP.");
        }

        String attemptsStr = stringRedisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_ATTEMPTS) {
            stringRedisTemplate.delete(otpKey);
            return OtpVerificationResult.invalidated(
                "Too many wrong attempts. Please request a new OTP.");
        }

        if (!storedOtp.equals(submittedOtp)) {
            int newAttempts = attempts + 1;
            stringRedisTemplate.opsForValue()
                .set(attemptsKey, String.valueOf(newAttempts), Duration.ofMinutes(OTP_TTL_MIN));

            if (newAttempts >= MAX_ATTEMPTS) {
                stringRedisTemplate.delete(otpKey);
                return OtpVerificationResult.invalidated(
                    "Too many wrong attempts. OTP invalidated. Please request a new OTP.");
            }

            return OtpVerificationResult.wrong(
                "Invalid OTP. " + (MAX_ATTEMPTS - newAttempts) + " attempts remaining.");
        }

        stringRedisTemplate.delete(otpKey);
        stringRedisTemplate.delete(attemptsKey);

        userRepository.findById(userId).ifPresent(user -> {
            user.setVerified(true);
            userRepository.save(user);
            log.info("User verified successfully → userId={}", userId);
        });

        return OtpVerificationResult.successResult();
    }

    private String generateOtp() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}
