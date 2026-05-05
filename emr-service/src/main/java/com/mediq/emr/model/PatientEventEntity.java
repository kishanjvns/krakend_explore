package com.mediq.emr.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_event_store", schema = "mediq_emr")
public class PatientEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EmrEventType eventType;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected PatientEventEntity() {}

    public PatientEventEntity(String patientId, EmrEventType eventType,
                               Long sequenceNumber, String payload, String recordedBy) {
        this.patientId = patientId;
        this.eventType = eventType;
        this.sequenceNumber = sequenceNumber;
        this.payload = payload;
        this.recordedBy = recordedBy;
    }

    public UUID getId() { return id; }
    public String getPatientId() { return patientId; }
    public EmrEventType getEventType() { return eventType; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public String getPayload() { return payload; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getOccurredAt() { return occurredAt; }
}
