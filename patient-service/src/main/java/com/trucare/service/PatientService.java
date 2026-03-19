package com.trucare.service;

import com.trucare.exception.PatientNotFoundException;
import com.trucare.model.Patient;
import com.trucare.model.PatientResponse;
import com.trucare.model.PatientStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer — owns all business logic.
 * Controllers delegate to this class; they never contain business rules.
 *
 * Spring Boot CONCEPT — @Service
 *   Marks this as a Spring-managed bean.
 *   Functionally equivalent to @Component but signals intent:
 *   this class lives in the service/domain layer.
 *   Spring Boot uses stereotype annotations for component scanning.
 *
 * Java 17 features used:
 *   - List.of()       : unmodifiable list (Java 9+)
 *   - Stream.toList() : terminal op returning unmodifiable List (Java 16+)
 *   - Method refs     : PatientResponse::from (Java 8+)
 *   - orElseThrow()   : Optional terminal with lambda supplier (Java 8+)
 */
@Service
public class PatientService {

    /**
     * In-memory store — simulates a real database for Step 2 learning.
     * PostgreSQL persistence is introduced in your polyglot project Phase 2.
     *
     * Java 9+: List.of() returns an UNMODIFIABLE list.
     * Any mutation attempt (add/remove) throws UnsupportedOperationException at runtime.
     * Java 8 equivalent: Collections.unmodifiableList(Arrays.asList(...))
     */
    private final List<Patient> patients = List.of(
            new Patient("P001", "Arun Sharma",    45,
                    "Hypertension",  "Dr. Mehta",  PatientStatus.from("admitted")),
            new Patient("P002", "Priya Verma",    32,
                    "Diabetes",      "Dr. Singh",  PatientStatus.from("outpatient")),
            new Patient("P003", "Ramesh Gupta",   60,
                    "Cardiac Issue", "Dr. Kapoor", PatientStatus.from("critical")),
            new Patient("P004", "Sunita Agarwal", 28,
                    "Fracture",      "Dr. Joshi",  PatientStatus.from("discharged"))
    );

    /**
     * Returns all patients as response DTOs.
     *
     * Java 16+: Stream.toList() — shorthand for collect(Collectors.toUnmodifiableList()).
     * Java 8 equivalent: .collect(Collectors.toList())
     */
    public List<PatientResponse> getAllPatients() {
        return patients.stream()
                .map(PatientResponse::from)
                .toList();
    }

    /**
     * Returns single patient by ID or throws PatientNotFoundException.
     *
     * Interview note:
     *   orElseThrow() with a lambda is the idiomatic Java 8+ pattern.
     *   Never call Optional.get() without isPresent() — it throws NoSuchElementException
     *   with no context, making debugging harder.
     */
    public PatientResponse getPatientById(String id) {
        return patients.stream()
                .filter(p -> p.id().equalsIgnoreCase(id))
                .map(PatientResponse::from)
                .findFirst()
                .orElseThrow(() -> new PatientNotFoundException(id));
    }

    /**
     * Returns patients filtered by status string.
     * PatientStatus.from() validates the status early — throws
     * IllegalArgumentException (mapped to 400) for unknown values.
     */
    public List<PatientResponse> getPatientsByStatus(String status) {
        PatientStatus target = PatientStatus.from(status);
        return patients.stream()
                .filter(p -> p.status().value().equalsIgnoreCase(target.value()))
                .map(PatientResponse::from)
                .toList();
    }

    /**
     * Returns only patients currently under active care.
     * Demonstrates the isActive() method from the sealed PatientStatus interface.
     * admitted + outpatient + critical = active; discharged = inactive.
     */
    public List<PatientResponse> getActivePatients() {
        return patients.stream()
                .filter(p -> p.status().isActive())
                .map(PatientResponse::from)
                .toList();
    }
}
