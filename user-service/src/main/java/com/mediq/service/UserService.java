package com.mediq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediq.dto.*;
import com.mediq.event.OtpRequestedEvent;
import com.mediq.event.UserEvent;
import com.mediq.exception.UserNotFoundException;
import com.mediq.model.*;
import com.mediq.repository.DoctorProfileRepository;
import com.mediq.repository.UserOutboxRepository;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final UserOutboxRepository outboxRepository;
    private final UserCacheService cacheService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       DoctorProfileRepository doctorProfileRepository,
                       UserOutboxRepository outboxRepository,
                       UserCacheService cacheService,
                       UserMapper userMapper,
                       ObjectMapper objectMapper,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.outboxRepository = outboxRepository;
        this.cacheService = cacheService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse registerPatient(RegisterPatientRequest request) {
        log.info("Registering patient: {}", request.firstName());

        UserEntity user = userMapper.toEntity(request, UserType.PATIENT);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        saveToOutbox(buildEvent("USER_REGISTERED", user, request.contacts()));
        saveOtpRequestsToOutbox(user, request.contacts());

        log.info("Patient registered: userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse registerDoctor(RegisterDoctorRequest request) {
        log.info("Registering doctor: {}", request.firstName());

        UserEntity user = userMapper.toEntity(request, UserType.DOCTOR);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        DoctorProfileEntity profile = new DoctorProfileEntity();
        profile.setUser(user);
        profile.setLicenseNumber(request.licenseNumber());
        profile.setLicenseExpiry(request.licenseExpiry());
        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setVerificationStatus(VerificationStatus.PENDING);
        doctorProfileRepository.save(profile);

        saveToOutbox(buildEvent("USER_REGISTERED", user, request.contacts()));
        saveOtpRequestsToOutbox(user, request.contacts());

        log.info("Doctor registered (PENDING verification): userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        UserResponse cached = cacheService.get(userId);
        if (cached != null) return cached;

        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        UserResponse response = userMapper.toResponse(user);
        cacheService.put(userId, response);
        return response;
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId, UUID requestedBy) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setActive(false);
        user.setUpdatedBy(requestedBy);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        cacheService.evict(userId);

        saveToOutbox(buildEvent("USER_DEACTIVATED", user,
            user.getContacts().stream()
                .map(c -> new ContactRequest(c.getContactType(), c.getContactValue(), c.isPrimary()))
                .toList()));

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse activateUser(UUID userId, UUID requestedBy) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setActive(true);
        user.setUpdatedBy(requestedBy);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        cacheService.evict(userId);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse changeUserRole(UUID userId, UserType newRole, UUID requestedBy) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setUserType(newRole);
        user.setUpdatedBy(requestedBy);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        cacheService.evict(userId);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse verifyDoctor(UUID doctorUserId, DoctorVerificationRequest request, UUID adminId) {
        UserEntity user = userRepository.findById(doctorUserId)
            .orElseThrow(() -> new UserNotFoundException(doctorUserId));

        DoctorProfileEntity profile = doctorProfileRepository.findByUserId(doctorUserId)
            .orElseThrow(() -> new IllegalStateException(
                "Doctor profile not found for userId: " + doctorUserId));

        profile.setVerificationStatus(request.status());
        profile.setVerifiedBy(adminId);
        profile.setVerifiedAt(Instant.now());

        if (request.status() == VerificationStatus.REJECTED) {
            profile.setRejectionReason(request.rejectionReason());
        }
        if (request.status() == VerificationStatus.VERIFIED) {
            user.setVerified(true);
        }

        doctorProfileRepository.save(profile);
        userRepository.save(user);
        cacheService.evict(doctorUserId);

        saveToOutbox(buildEvent("DOCTOR_VERIFIED", user,
            user.getContacts().stream()
                .map(c -> new ContactRequest(c.getContactType(), c.getContactValue(), c.isPrimary()))
                .toList()));

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getPendingDoctorVerifications() {
        return doctorProfileRepository
            .findByVerificationStatus(VerificationStatus.PENDING)
            .stream()
            .map(p -> userMapper.toResponse(p.getUser()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
            .map(userMapper::toResponse)
            .toList();
    }

    private void saveOtpRequestsToOutbox(UserEntity user, List<ContactRequest> contacts) {
        String userName = user.getFirstName() + " " + user.getLastName();
        contacts.forEach(contact -> {
            OtpRequestedEvent event = OtpRequestedEvent.of(
                user.getId().toString(),
                contact.contactType().name(),
                contact.contactValue(),
                userName
            );
            try {
                UserOutboxEntity outbox = new UserOutboxEntity();
                outbox.setAggregateId(user.getId().toString());
                outbox.setEventType("OTP_REQUESTED");
                outbox.setDestination("mediq.user.events");
                outbox.setTimestamp(Instant.now().toEpochMilli());
                outbox.setPayload(objectMapper.writeValueAsString(event));
                outboxRepository.save(outbox);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize OTP event for outbox: " + e.getMessage(), e);
            }
        });
    }

    private void saveToOutbox(UserEvent event) {
        try {
            UserOutboxEntity outbox = new UserOutboxEntity();
            outbox.setAggregateId(event.userId());
            outbox.setEventType(event.eventType());
            outbox.setDestination("mediq.user.events");
            outbox.setTimestamp(Instant.now().toEpochMilli());
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox: " + e.getMessage(), e);
        }
    }

    private UserEvent buildEvent(String eventType, UserEntity user, List<ContactRequest> contacts) {
        String email = contacts.stream()
            .filter(c -> c.contactType() == ContactType.EMAIL && c.isPrimary())
            .map(ContactRequest::contactValue)
            .findFirst().orElse(null);

        String phone = contacts.stream()
            .filter(c -> c.contactType() == ContactType.PHONE && c.isPrimary())
            .map(ContactRequest::contactValue)
            .findFirst().orElse(null);

        String verificationStatus = user.getDoctorProfile() != null
            ? user.getDoctorProfile().getVerificationStatus().name()
            : null;

        return UserEvent.of(eventType,
            user.getId().toString(),
            user.getUserType().name(),
            user.getFirstName(),
            user.getLastName(),
            email, phone, verificationStatus);
    }
}
