package com.mediq.service;

import com.mediq.dto.*;
import com.mediq.event.UserEvent;
import com.mediq.event.UserEventPublisher;
import com.mediq.exception.UserNotFoundException;
import com.mediq.model.*;
import com.mediq.repository.DoctorProfileRepository;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final UserEventPublisher eventPublisher;
    private final UserCacheService cacheService;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       DoctorProfileRepository doctorProfileRepository,
                       UserEventPublisher eventPublisher,
                       UserCacheService cacheService,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse registerPatient(RegisterPatientRequest request) {
        log.info("Registering patient: {}", request.firstName());

        UserEntity user = userMapper.toEntity(request, UserType.PATIENT);
        userRepository.save(user);

        UserEvent event = buildEvent("USER_REGISTERED", user, request.contacts());
        eventPublisher.publish(event);

        log.info("Patient registered: userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse registerDoctor(RegisterDoctorRequest request) {
        log.info("Registering doctor: {}", request.firstName());

        UserEntity user = userMapper.toEntity(request, UserType.DOCTOR);
        userRepository.save(user);

        DoctorProfileEntity profile = new DoctorProfileEntity();
        profile.setUser(user);
        profile.setLicenseNumber(request.licenseNumber());
        profile.setLicenseExpiry(request.licenseExpiry());
        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setVerificationStatus(VerificationStatus.PENDING);
        doctorProfileRepository.save(profile);

        UserEvent event = buildEvent("USER_REGISTERED", user, request.contacts());
        eventPublisher.publish(event);

        log.info("Doctor registered (PENDING verification): userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        UserResponse cached = cacheService.get(userId);
        if (cached != null) return cached;

        UserEntity user = userRepository.findByIdWithDetails(userId)
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

        UserEvent event = buildEvent("USER_DEACTIVATED", user,
            user.getContacts().stream()
                .map(c -> new ContactRequest(c.getContactType(), c.getContactValue(), c.isPrimary()))
                .toList());
        eventPublisher.publish(event);

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse verifyDoctor(UUID doctorUserId, DoctorVerificationRequest request, UUID adminId) {
        UserEntity user = userRepository.findByIdWithDetails(doctorUserId)
            .orElseThrow(() -> new UserNotFoundException(doctorUserId));

        DoctorProfileEntity profile = doctorProfileRepository.findByUserId(doctorUserId)
            .orElseThrow(() -> new IllegalStateException("Doctor profile not found for userId: " + doctorUserId));

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

        UserEvent event = buildEvent("DOCTOR_VERIFIED", user,
            user.getContacts().stream()
                .map(c -> new ContactRequest(c.getContactType(), c.getContactValue(), c.isPrimary()))
                .toList());
        eventPublisher.publish(event);

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

    private UserEvent buildEvent(String eventType, UserEntity user,
                                 List<ContactRequest> contacts) {
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
            user.getKeycloakId(),
            user.getUserType().name(),
            user.getFirstName(),
            user.getLastName(),
            email, phone, verificationStatus);
    }
}
