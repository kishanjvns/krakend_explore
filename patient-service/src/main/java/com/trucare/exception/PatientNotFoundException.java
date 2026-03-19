package com.trucare.exception;

/**
 * Domain-specific exception for patient-not-found scenarios.
 *
 * Spring Boot CONCEPT:
 *   Keeping a dedicated exception (rather than throwing generic
 *   RuntimeException) allows GlobalExceptionHandler to map it
 *   to exactly HTTP 404 — clean, explicit, testable.
 *
 * Interview note:
 *   Never let JPA/repository-layer exceptions (like EmptyResultDataAccessException)
 *   bubble up to the controller raw. Always wrap them in meaningful domain
 *   exceptions and handle centrally via @RestControllerAdvice.
 */
public class PatientNotFoundException extends RuntimeException {

    private final String patientId;

    public PatientNotFoundException(String patientId) {
        super("Patient not found with id: " + patientId);
        this.patientId = patientId;
    }

    public String getPatientId() {
        return patientId;
    }
}
