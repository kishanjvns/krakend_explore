package com.mediq.doctor.service;

import com.mediq.doctor.dto.AddSpecializationRequest;
import com.mediq.doctor.dto.DoctorProfileResponse;
import com.mediq.doctor.dto.DoctorSearchResponse;
import com.mediq.doctor.dto.SetAvailabilityRequest;
import com.mediq.doctor.event.DoctorEvent;
import com.mediq.doctor.event.UserEvent;
import com.mediq.doctor.model.DoctorAvailabilityEntity;
import com.mediq.doctor.model.DoctorProfileEntity;
import com.mediq.doctor.model.DoctorSearchViewEntity;
import com.mediq.doctor.model.DoctorSpecializationEntity;
import com.mediq.doctor.repository.DoctorAvailabilityRepository;
import com.mediq.doctor.repository.DoctorProfileRepository;
import com.mediq.doctor.repository.DoctorSearchViewRepository;
import com.mediq.doctor.repository.DoctorSpecializationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorService.class);

    private final DoctorProfileRepository doctorProfileRepository;
    private final DoctorSpecializationRepository doctorSpecializationRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;
    private final DoctorSearchViewRepository doctorSearchViewRepository;
    private final KafkaTemplate<String, DoctorEvent> kafkaTemplate;

    @Value("${mediq.kafka.topic.doctor-events}")
    private String doctorEventsTopic;

    public DoctorService(DoctorProfileRepository doctorProfileRepository,
                         DoctorSpecializationRepository doctorSpecializationRepository,
                         DoctorAvailabilityRepository doctorAvailabilityRepository,
                         DoctorSearchViewRepository doctorSearchViewRepository,
                         KafkaTemplate<String, DoctorEvent> kafkaTemplate) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.doctorSpecializationRepository = doctorSpecializationRepository;
        this.doctorAvailabilityRepository = doctorAvailabilityRepository;
        this.doctorSearchViewRepository = doctorSearchViewRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Creates a stub doctor profile when a USER_REGISTERED event arrives for a DOCTOR user type.
     */
    @Transactional
    public void createDoctorStub(UserEvent event) {
        UUID userId = UUID.fromString(event.userId());

        // Idempotency: skip if profile already exists
        if (doctorProfileRepository.findByUserId(userId).isPresent()) {
            log.info("Doctor profile already exists for userId={}, skipping creation", userId);
            return;
        }

        DoctorProfileEntity profile = new DoctorProfileEntity();
        profile.setUserId(userId);
        profile.setFirstName(event.firstName() != null ? event.firstName() : "");
        profile.setLastName(event.lastName() != null ? event.lastName() : "");
        // License placeholder — real license comes from user-service data later
        profile.setLicenseNumber("PENDING-" + userId);
        profile.setYearsOfExperience(0);
        profile.setActive(true);
        profile.setVerified(false);

        DoctorProfileEntity saved = doctorProfileRepository.save(profile);
        log.info("Created doctor stub profile id={} for userId={}", saved.getId(), userId);

        DoctorEvent doctorEvent = DoctorEvent.of("DOCTOR_STUB_CREATED", saved);
        kafkaTemplate.send(doctorEventsTopic, saved.getId().toString(), doctorEvent);
    }

    /**
     * Marks a doctor as verified and upserts the doctor_search_view.
     */
    @Transactional
    public void activateDoctorInSearch(UserEvent event) {
        UUID userId = UUID.fromString(event.userId());
        DoctorProfileEntity profile = doctorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor profile not found for userId=" + userId));

        profile.setVerified(true);
        doctorProfileRepository.save(profile);

        upsertSearchView(profile);
        log.info("Doctor activated in search: doctorId={}, userId={}", profile.getId(), userId);
    }

    /**
     * Deactivates a doctor from the search view.
     */
    @Transactional
    public void deactivateDoctorFromSearch(UserEvent event) {
        UUID userId = UUID.fromString(event.userId());
        DoctorProfileEntity profile = doctorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor profile not found for userId=" + userId));

        profile.setActive(false);
        doctorProfileRepository.save(profile);

        Optional<DoctorSearchViewEntity> viewOpt = doctorSearchViewRepository.findById(profile.getId());
        viewOpt.ifPresent(view -> {
            view.setActive(false);
            doctorSearchViewRepository.save(view);
        });

        log.info("Doctor deactivated from search: doctorId={}, userId={}", profile.getId(), userId);
    }

    /**
     * Adds a specialization to a doctor and refreshes the search view.
     */
    @Transactional
    public void addSpecialization(UUID doctorId, AddSpecializationRequest req) {
        DoctorProfileEntity profile = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found: " + doctorId));

        DoctorSpecializationEntity spec = new DoctorSpecializationEntity();
        spec.setDoctorId(doctorId);
        spec.setSpecialization(req.specialization());
        spec.setPrimary(req.primary());
        doctorSpecializationRepository.save(spec);

        log.info("Added specialization '{}' to doctorId={}", req.specialization(), doctorId);
        upsertSearchView(profile);
    }

    /**
     * Saves an availability slot and updates available_days in the search view.
     */
    @Transactional
    public void setAvailability(UUID doctorId, SetAvailabilityRequest req) {
        DoctorProfileEntity profile = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found: " + doctorId));

        DoctorAvailabilityEntity availability = new DoctorAvailabilityEntity();
        availability.setDoctorId(doctorId);
        availability.setDayOfWeek(req.dayOfWeek());
        availability.setStartTime(req.startTime());
        availability.setEndTime(req.endTime());
        availability.setSlotDuration(req.slotDuration() > 0 ? req.slotDuration() : 30);
        availability.setActive(true);
        doctorAvailabilityRepository.save(availability);

        log.info("Set availability for doctorId={} on {}", doctorId, req.dayOfWeek());
        upsertSearchView(profile);
    }

    /**
     * Retrieves a doctor profile by doctorId.
     */
    @Transactional(readOnly = true)
    public DoctorProfileResponse getDoctorProfile(UUID doctorId) {
        DoctorProfileEntity profile = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found: " + doctorId));

        return toProfileResponse(profile);
    }

    /**
     * Searches doctors from the search view by optional specialization and verified flag.
     */
    @Transactional(readOnly = true)
    public List<DoctorSearchResponse> searchDoctors(String specialization, Boolean verified) {
        List<DoctorSearchViewEntity> results = doctorSearchViewRepository.searchDoctors(specialization, verified);
        return results.stream()
                .map(this::toSearchResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void upsertSearchView(DoctorProfileEntity profile) {
        List<DoctorSpecializationEntity> specs =
                doctorSpecializationRepository.findByDoctorId(profile.getId());

        String primarySpec = specs.stream()
                .filter(DoctorSpecializationEntity::isPrimary)
                .findFirst()
                .map(DoctorSpecializationEntity::getSpecialization)
                .orElse(specs.isEmpty() ? null : specs.get(0).getSpecialization());

        String[] allSpecs = specs.stream()
                .map(DoctorSpecializationEntity::getSpecialization)
                .toArray(String[]::new);

        List<DoctorAvailabilityEntity> availabilities =
                doctorAvailabilityRepository.findByDoctorIdAndActive(profile.getId(), true);

        String[] availableDays = availabilities.stream()
                .map(DoctorAvailabilityEntity::getDayOfWeek)
                .distinct()
                .toArray(String[]::new);

        DoctorSearchViewEntity view = doctorSearchViewRepository
                .findById(profile.getId())
                .orElseGet(() -> {
                    DoctorSearchViewEntity newView = new DoctorSearchViewEntity();
                    newView.setId(profile.getId());
                    newView.setUserId(profile.getUserId());
                    return newView;
                });

        view.setFullName(profile.getFirstName() + " " + profile.getLastName());
        view.setPrimarySpecialization(primarySpec);
        view.setAllSpecializations(allSpecs);
        view.setConsultationFee(profile.getConsultationFee());
        view.setYearsOfExperience(profile.getYearsOfExperience());
        view.setVerified(profile.isVerified());
        view.setActive(profile.isActive());
        view.setAvailableDays(availableDays);

        doctorSearchViewRepository.save(view);
        log.debug("Upserted search view for doctorId={}", profile.getId());
    }

    private DoctorProfileResponse toProfileResponse(DoctorProfileEntity profile) {
        return new DoctorProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getYearsOfExperience(),
                profile.getConsultationFee(),
                profile.isVerified(),
                profile.isActive(),
                profile.getCreatedAt()
        );
    }

    private DoctorSearchResponse toSearchResponse(DoctorSearchViewEntity view) {
        return new DoctorSearchResponse(
                view.getId(),
                view.getUserId(),
                view.getFullName(),
                view.getPrimarySpecialization(),
                view.getAllSpecializations(),
                view.getConsultationFee(),
                view.getYearsOfExperience(),
                view.isVerified(),
                view.getAvailableDays()
        );
    }
}
