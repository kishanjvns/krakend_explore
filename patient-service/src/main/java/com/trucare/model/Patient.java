package com.trucare.model;

/**
 * Java 17 FEATURE — Record
 *
 * A record is a special restricted class that is:
 *   - Immutable by default (all components are final)
 *   - Auto-generates: canonical constructor, getters (id(), name()...),
 *     equals(), hashCode(), toString()
 *
 * Java 8 equivalent: a full POJO with private final fields,
 * all-args constructor, getters, and hand-written equals/hashCode/toString
 * (or Lombok @Value).
 *
 * Interview note:
 *   Records are ideal for DTOs and value objects.
 *   They cannot extend other classes (implicitly extend java.lang.Record)
 *   but CAN implement interfaces — as PatientStatus shows.
 */
public record Patient(
        String id,
        String name,
        int age,
        String diagnosis,
        String assignedDoctor,
        PatientStatus status
) {
    /**
     * Java 17 FEATURE — Compact constructor.
     * Runs before the auto-generated canonical constructor.
     * Parameters are already assigned when this block runs —
     * you can only validate or normalise, not reassign final fields directly.
     *
     * Java 8 equivalent: validation inside the constructor body.
     */
    public Patient {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Patient id must not be blank");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Patient name must not be blank");
        if (age <= 0)
            throw new IllegalArgumentException("Age must be positive");
    }
}
