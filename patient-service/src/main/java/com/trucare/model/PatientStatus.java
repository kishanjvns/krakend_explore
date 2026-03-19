package com.trucare.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Java 17 FEATURE — Sealed Interface
 *
 * A sealed interface restricts which classes/records can implement it.
 * Only the types listed in "permits" are valid subtypes.
 * This makes the hierarchy CLOSED and EXHAUSTIVE —
 * the compiler knows every possible subtype at compile time.
 *
 * Java 8 equivalent: an enum — but enums cannot carry per-variant behaviour
 * as cleanly, and cannot hold state beyond enum constants.
 *
 * Interview note:
 *   Sealed types + pattern matching switch (Java 21 standard, Java 17 preview)
 *   are the foundation of Algebraic Data Types (ADTs) in Java.
 *   This pattern is heavily used in modern domain modelling.
 *
 * Why sealed here instead of enum?
 *   Each status variant has its own behaviour (isActive()).
 *   With enum you'd need a switch inside isActive().
 *   With sealed records, each variant IS its own class and owns its logic.
 */
public sealed interface PatientStatus
        permits PatientStatus.Admitted,
                PatientStatus.Outpatient,
                PatientStatus.Critical,
                PatientStatus.Discharged {

    /**
     * @JsonValue tells Jackson to use this method's return value
     * when serialising this type to JSON.
     */
    @JsonValue
    String value();

    /** Whether this patient is currently under active care. */
    boolean isActive();

    /**
     * Java 17 FEATURE — Switch expression as a static factory.
     *
     * Switch EXPRESSION (not statement): returns a value directly.
     * Arrow cases (->): no fall-through, no break needed.
     *
     * Java 8 equivalent:
     *   switch (raw) {
     *     case "admitted": return new Admitted(); break;
     *     ...
     *   }
     *   or a Map<String, Supplier<PatientStatus>> lookup.
     */
    static PatientStatus from(String raw) {
        return switch (raw.toLowerCase()) {
            case "admitted"   -> new Admitted();
            case "outpatient" -> new Outpatient();
            case "critical"   -> new Critical();
            case "discharged" -> new Discharged();
            default -> throw new IllegalArgumentException(
                    "Unknown patient status: " + raw);
        };
    }

    // ── Permitted record implementations ────────────────────────────────────
    // Each is a record (immutable, no fields needed) that implements this interface.

    record Admitted()   implements PatientStatus {
        @Override public String  value()    { return "admitted";   }
        @Override public boolean isActive() { return true;         }
    }

    record Outpatient() implements PatientStatus {
        @Override public String  value()    { return "outpatient"; }
        @Override public boolean isActive() { return true;         }
    }

    record Critical()   implements PatientStatus {
        @Override public String  value()    { return "critical";   }
        @Override public boolean isActive() { return true;         }
    }

    record Discharged() implements PatientStatus {
        @Override public String  value()    { return "discharged"; }
        @Override public boolean isActive() { return false;        }
    }
}
