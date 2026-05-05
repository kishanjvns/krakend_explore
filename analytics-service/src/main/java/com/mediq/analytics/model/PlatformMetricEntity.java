package com.mediq.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_metric", schema = "mediq_analytics")
public class PlatformMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "metric_name", nullable = false, unique = true)
    private String metricName;

    @Column(name = "metric_value", nullable = false)
    private long metricValue = 0L;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    protected PlatformMetricEntity() {}

    public UUID getId() { return id; }
    public String getMetricName() { return metricName; }
    public long getMetricValue() { return metricValue; }
    public void setMetricValue(long metricValue) { this.metricValue = metricValue; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
