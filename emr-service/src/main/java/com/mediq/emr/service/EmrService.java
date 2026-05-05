package com.mediq.emr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediq.emr.model.EmrEventType;
import com.mediq.emr.model.PatientEventEntity;
import com.mediq.emr.model.PatientSummaryEntity;
import com.mediq.emr.repository.PatientEventRepository;
import com.mediq.emr.repository.PatientSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmrService {

    private final PatientEventRepository eventRepository;
    private final PatientSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;

    public EmrService(PatientEventRepository eventRepository,
                      PatientSummaryRepository summaryRepository,
                      ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.summaryRepository = summaryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PatientEventEntity appendEvent(String patientId, EmrEventType eventType,
                                           String payload, String recordedBy) {
        // Acquire pessimistic lock on summary row to serialize appends per patient
        PatientSummaryEntity summary = summaryRepository.findByPatientIdForUpdate(patientId)
                .orElseGet(() -> summaryRepository.save(new PatientSummaryEntity(patientId)));

        long nextSeq = eventRepository.findMaxSequenceByPatientId(patientId)
                .map(s -> s + 1).orElse(1L);

        PatientEventEntity event = new PatientEventEntity(patientId, eventType, nextSeq, payload, recordedBy);
        PatientEventEntity saved = eventRepository.save(event);

        applyEvent(summary, saved);
        summary.setLastUpdated(Instant.now());
        summaryRepository.save(summary);

        return saved;
    }

    @Transactional(readOnly = true)
    public PatientSummaryEntity getCurrentState(String patientId) {
        return summaryRepository.findById(patientId)
                .orElse(new PatientSummaryEntity(patientId));
    }

    @Transactional(readOnly = true)
    public List<PatientEventEntity> getHistory(String patientId) {
        return eventRepository.findByPatientIdOrderBySequenceNumberAsc(patientId);
    }

    @Transactional(readOnly = true)
    public PatientSummaryEntity replayToDate(String patientId, LocalDate asOfDate) {
        Instant cutoff = asOfDate.plusDays(1).atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC);
        List<PatientEventEntity> events = eventRepository
                .findByPatientIdAndOccurredAtBeforeOrderBySequenceNumberAsc(patientId, cutoff);

        PatientSummaryEntity snapshot = new PatientSummaryEntity(patientId);
        for (PatientEventEntity event : events) {
            applyEvent(snapshot, event);
        }
        return snapshot;
    }

    void applyEvent(PatientSummaryEntity summary, PatientEventEntity event) {
        try {
            JsonNode node = objectMapper.readTree(event.getPayload());
            switch (event.getEventType()) {
                case DIAGNOSIS_ADDED -> {
                    String condition = node.path("condition").asText();
                    List<String> conditions = new ArrayList<>(summary.getActiveConditions());
                    if (!conditions.contains(condition)) conditions.add(condition);
                    summary.setActiveConditions(conditions);
                }
                case DIAGNOSIS_RESOLVED -> {
                    String condition = node.path("condition").asText();
                    List<String> conditions = new ArrayList<>(summary.getActiveConditions());
                    conditions.remove(condition);
                    summary.setActiveConditions(conditions);
                }
                case MEDICATION_PRESCRIBED -> {
                    String medication = node.path("medication").asText();
                    List<String> meds = new ArrayList<>(summary.getCurrentMedications());
                    if (!meds.contains(medication)) meds.add(medication);
                    summary.setCurrentMedications(meds);
                }
                case MEDICATION_DISCONTINUED -> {
                    String medication = node.path("medication").asText();
                    List<String> meds = new ArrayList<>(summary.getCurrentMedications());
                    meds.remove(medication);
                    summary.setCurrentMedications(meds);
                }
                case ALLERGY_RECORDED -> {
                    String allergy = node.path("allergy").asText();
                    List<String> allergies = new ArrayList<>(summary.getAllergies());
                    if (!allergies.contains(allergy)) allergies.add(allergy);
                    summary.setAllergies(allergies);
                }
                case ALLERGY_REMOVED -> {
                    String allergy = node.path("allergy").asText();
                    List<String> allergies = new ArrayList<>(summary.getAllergies());
                    allergies.remove(allergy);
                    summary.setAllergies(allergies);
                }
                case VISIT_RECORDED -> {
                    if (node.has("doctorId")) summary.setPrimaryDoctorId(node.path("doctorId").asText());
                    summary.setLastVisitDate(LocalDate.now());
                }
                case LAB_RESULT_RECORDED, VITAL_SIGNS_RECORDED,
                        PROCEDURE_PERFORMED, REFERRAL_ISSUED, EMERGENCY_ALERT -> {
                    // Informational — no read-model mutation needed
                }
            }
        } catch (Exception ignored) {
            // Malformed payload — skip without poisoning the event stream
        }
    }
}
