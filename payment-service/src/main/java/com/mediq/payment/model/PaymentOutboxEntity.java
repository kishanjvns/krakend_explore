package com.mediq.payment.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_outbox", schema = "mediq_payments")
public class PaymentOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public PaymentOutboxEntity() {}

    public PaymentOutboxEntity(String aggregateId, String aggregateType,
                                String eventType, String destination,
                                String payload) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.destination = destination;
        this.payload = payload;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public UUID getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public String getEventType() { return eventType; }
    public String getDestination() { return destination; }
    public String getPayload() { return payload; }
    public Long getTimestamp() { return timestamp; }
    public Instant getCreatedAt() { return createdAt; }
}
