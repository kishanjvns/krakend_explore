package com.trucare.model;

/**
 * Java 17 FEATURE — Record as a Response DTO
 *
 * Separating the internal domain model (Patient) from the API response
 * shape (PatientResponse) is a clean microservices / layered architecture practice.
 *
 * KrakenD receives this JSON at its backend layer and can further reshape
 * it in Step 6 (transformations). By having a dedicated DTO you control
 * exactly what fields cross the service boundary.
 *
 * Interview note:
 *   Always separate domain model from API contract (DTO pattern).
 *   This lets you change internals without breaking clients.
 *   In Spring Boot projects this is sometimes done with MapStruct for
 *   large codebases — here we use a static factory method for simplicity.
 */
public record PatientResponse(
        String id,
        String name,
        int age,
        String diagnosis,
        String assignedDoctor,
        String status,          // serialised string value from PatientStatus
        boolean isActive,       // derived from PatientStatus.isActive()
        String serviceSource    // for debugging — shows which pod responded
) {
    /**
     * Static factory — maps domain model → response DTO.
     *
     * Java 8 equivalent: a separate PatientMapper class or hand-written
     * constructor calls in each controller method.
     */
    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.id(),
                patient.name(),
                patient.age(),
                patient.diagnosis(),
                patient.assignedDoctor(),
                patient.status().value(),
                patient.status().isActive(),
                "patient-service"
        );
    }
}
