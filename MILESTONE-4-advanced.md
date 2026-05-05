# mediq — Milestone 4: Advanced Patterns + Observability

## Branch
```powershell
git checkout feature/mediq-m3-kubernetes
git checkout -b feature/mediq-m4-advanced
```

## What This Milestone Covers
```
M4a: emr-service — Event Sourcing for patient medical records
M4b: analytics-service — CQRS cross-service dashboard read model
M4c: Observability stack — Prometheus + Grafana + Loki alerts
M4d: BFF pattern in KrakenD — mobile vs web response shapes
```

## New Services
```
emr-service/          ← NEW — port 8086 — Event Sourcing
analytics-service/    ← NEW — port 8087 — CQRS dashboards
```

---

## TASK-M4a: emr-service (Electronic Medical Records)

### Why Event Sourcing here
```
Traditional approach (update-in-place):
  Patient admitted → INSERT patient record
  Doctor updates diagnosis → UPDATE patient SET diagnosis = 'Diabetes'
  Medication changed → UPDATE patient SET medication = 'Metformin'
  
  Result: you only see the CURRENT state
  Question: "What was this patient's diagnosis 3 months ago?"
  Answer: impossible — overwritten forever

Event Sourcing approach:
  Patient admitted → INSERT PatientAdmitted event
  Doctor diagnoses → INSERT DiagnosisAdded event
  Medication added → INSERT MedicationPrescribed event
  
  Result: full immutable history of EVERYTHING that happened
  Question: "What was the diagnosis 3 months ago?"
  Answer: replay events up to that date → exact state at any point
  
  Medical records LEGALLY require this audit trail
  HIPAA: you must be able to reproduce the exact state of a
  patient record at any point in time
```

### Package
`com.mediq.emr`

### Port: 8086
### Database: `mediq_emr`

### Flyway migration `V1__create_emr_schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_emr;

-- EVENT STORE — the source of truth
-- Every change to a patient record is an event, NEVER an update
-- Events are IMMUTABLE — never deleted, never updated
CREATE TABLE patient_event_store (
    id              BIGSERIAL PRIMARY KEY,       -- sequential for ordering
    event_id        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL,               -- patient user_id
    aggregate_type  VARCHAR(50) NOT NULL DEFAULT 'PATIENT',
    event_type      VARCHAR(100) NOT NULL,
    event_version   INT NOT NULL DEFAULT 1,      -- for schema evolution
    payload         JSONB NOT NULL,              -- event data
    created_by      UUID NOT NULL,               -- doctor/nurse who made the change
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Optimistic concurrency control
    -- Prevents two concurrent writes creating conflicting events
    sequence_number BIGINT NOT NULL              -- per-patient sequence
);

-- Unique constraint: each patient can only have one event at each sequence position
-- Prevents duplicate events from concurrent writes
CREATE UNIQUE INDEX idx_event_store_patient_seq
    ON patient_event_store(patient_id, sequence_number);

-- Fast lookup by patient
CREATE INDEX idx_event_store_patient_time
    ON patient_event_store(patient_id, created_at);

-- Event type lookup (for projections)
CREATE INDEX idx_event_store_type
    ON patient_event_store(event_type);

-- READ MODEL — current patient summary (rebuilt from events)
-- This is the CQRS read side of emr-service
-- Rebuilt by replaying all events for a patient
CREATE TABLE patient_summary (
    patient_id          UUID PRIMARY KEY,
    full_name           VARCHAR(200),
    date_of_birth       DATE,
    active_diagnoses    JSONB,           -- array of current diagnosis objects
    current_medications JSONB,           -- array of active medication objects
    last_visit_date     DATE,
    admission_status    VARCHAR(20),     -- ADMITTED, OUTPATIENT, DISCHARGED
    last_updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_sequence       BIGINT NOT NULL DEFAULT 0  -- last event applied
);
```

### Event Types

```java
// All events that can happen to a patient in EMR:
public enum EmrEventType {
    PATIENT_REGISTERED,      // patient first appears in EMR system
    PATIENT_ADMITTED,        // admitted to hospital/clinic
    DIAGNOSIS_ADDED,         // new diagnosis recorded
    DIAGNOSIS_RESOLVED,      // diagnosis marked as resolved
    MEDICATION_PRESCRIBED,   // new medication added
    MEDICATION_DISCONTINUED, // medication stopped
    LAB_RESULT_RECORDED,     // lab test result added
    VITAL_SIGNS_RECORDED,    // blood pressure, temperature, etc
    APPOINTMENT_COMPLETED,   // doctor visit completed
    PATIENT_DISCHARGED,      // discharged from admission
    ALLERGY_RECORDED,        // allergy noted
    CLINICAL_NOTE_ADDED      // free-text clinical note
}
```

### Key Java classes

**`PatientEventEntity.java`**:
```java
@Entity
@Table(name = "patient_event_store", schema = "mediq_emr")
public class PatientEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true)
    private UUID eventId = UUID.randomUUID();

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EmrEventType eventType;

    @Column(name = "event_version")
    private int eventVersion = 1;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;     // JSON string of event-specific data

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sequence_number")
    private Long sequenceNumber;
    // getters/setters
}
```

**`EmrService.java`** — the core event sourcing logic:
```java
@Service
public class EmrService {

    @Transactional
    public PatientEventEntity appendEvent(UUID patientId,
                                          EmrEventType eventType,
                                          Object eventData,
                                          UUID createdBy) {
        // Step 1: Get next sequence number for this patient
        // Uses SELECT MAX(sequence_number) + 1 ... FOR UPDATE
        // Prevents two concurrent writers getting the same sequence number
        Long nextSeq = eventRepository.getNextSequence(patientId);

        // Step 2: Create and save event
        PatientEventEntity event = new PatientEventEntity();
        event.setPatientId(patientId);
        event.setEventType(eventType);
        event.setPayload(objectMapper.writeValueAsString(eventData));
        event.setCreatedBy(createdBy);
        event.setSequenceNumber(nextSeq);
        eventRepository.save(event);

        // Step 3: Update read model (patient_summary) asynchronously
        // or synchronously in same transaction for strong consistency
        updateReadModel(patientId, event);

        return event;
    }

    @Transactional(readOnly = true)
    public PatientState replayToDate(UUID patientId, Instant asOfDate) {
        // Replay all events up to asOfDate — gives exact historical state
        List<PatientEventEntity> events = eventRepository
            .findByPatientIdAndCreatedAtBeforeOrderBySequenceNumberAsc(
                patientId, asOfDate);

        return events.stream()
            .reduce(PatientState.empty(), this::applyEvent, (a, b) -> b);
    }

    @Transactional(readOnly = true)
    public PatientState getCurrentState(UUID patientId) {
        // Read from patient_summary (pre-computed CQRS read model)
        // Faster than replaying all events each time
        return patientSummaryRepository.findById(patientId)
            .map(patientMapper::toState)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
    }

    private PatientState applyEvent(PatientState state, PatientEventEntity event) {
        // Pure function — applies one event to current state
        // No side effects — used for both replay and current state
        return switch (event.getEventType()) {
            case DIAGNOSIS_ADDED -> state.withDiagnosis(
                parsePayload(event.getPayload(), DiagnosisPayload.class));
            case DIAGNOSIS_RESOLVED -> state.resolveDiagnosis(
                parsePayload(event.getPayload(), DiagnosisPayload.class).getDiagnosisId());
            case MEDICATION_PRESCRIBED -> state.withMedication(
                parsePayload(event.getPayload(), MedicationPayload.class));
            case MEDICATION_DISCONTINUED -> state.discontinueMedication(
                parsePayload(event.getPayload(), MedicationPayload.class).getMedicationId());
            case PATIENT_ADMITTED -> state.withStatus("ADMITTED");
            case PATIENT_DISCHARGED -> state.withStatus("DISCHARGED");
            default -> state;  // events that don't change summary state
        };
    }
}
```

**`EmrController.java`** — REST endpoints:
```
POST /emr/patients/{patientId}/events/diagnosis       ← add diagnosis
POST /emr/patients/{patientId}/events/medication      ← prescribe medication
POST /emr/patients/{patientId}/events/vitals          ← record vitals
POST /emr/patients/{patientId}/events/note            ← add clinical note
POST /emr/patients/{patientId}/events/admit           ← admit patient
POST /emr/patients/{patientId}/events/discharge       ← discharge patient

GET  /emr/patients/{patientId}/current                ← current state (from read model)
GET  /emr/patients/{patientId}/history                ← full event history
GET  /emr/patients/{patientId}/as-of?date=2024-01-15  ← historical state replay
```

### application.properties for emr-service
```properties
server.port=8086
spring.application.name=emr-service
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediq_emr}
spring.datasource.username=${DB_USERNAME:mediq}
spring.datasource.password=${DB_PASSWORD:mediq}
spring.flyway.schemas=mediq_emr
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
mediq.kafka.topic.appointment-events=mediq.appointment.events
mediq.kafka.topic.emr-events=mediq.emr.events
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.tracing.sampling.probability=1.0
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

---

## TASK-M4b: analytics-service

### Why CQRS Here
```
Query: "Show me daily appointment counts by specialization for last 30 days"
  
Without CQRS:
  SELECT
    DATE(a.booked_at) as day,
    ds.specialization,
    COUNT(*) as total
  FROM mediq_appointments.appointment a
  JOIN mediq_doctors.doctor_profile dp ON a.doctor_id = dp.user_id
  JOIN mediq_doctors.doctor_specialization ds ON dp.id = ds.doctor_id
  WHERE a.booked_at >= NOW() - INTERVAL '30 days'
  GROUP BY day, specialization
  ORDER BY day DESC
  
  Problems:
  → Cross-service join (appointment DB + doctor DB) — IMPOSSIBLE in microservices
  → Even in monolith: heavy aggregation query runs on write DB
  → Runs on admin dashboard refresh every 30 seconds

With CQRS read model:
  SELECT day, specialization, total_appointments
  FROM analytics_daily_summary
  WHERE day >= NOW() - INTERVAL '30 days'
  ORDER BY day DESC
  
  → Single table, pre-computed, sub-10ms
  → Updated asynchronously from Kafka events
  → Write DBs completely unaffected
```

### Package
`com.mediq.analytics`

### Port: 8087
### Database: `mediq_analytics`

### Flyway migration `V1__create_analytics_schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_analytics;

-- Daily appointment summary (CQRS read model)
-- Pre-computed from appointment events
CREATE TABLE daily_appointment_summary (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_date        DATE NOT NULL,
    specialization      VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN',
    total_booked        INT NOT NULL DEFAULT 0,
    total_confirmed     INT NOT NULL DEFAULT 0,
    total_cancelled     INT NOT NULL DEFAULT 0,
    total_completed     INT NOT NULL DEFAULT 0,
    total_no_show       INT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (summary_date, specialization)
);

-- Doctor performance summary (CQRS read model)
CREATE TABLE doctor_performance_summary (
    doctor_id           UUID PRIMARY KEY,
    full_name           VARCHAR(200),
    specialization      VARCHAR(100),
    total_appointments  INT NOT NULL DEFAULT 0,
    completed_count     INT NOT NULL DEFAULT 0,
    cancelled_count     INT NOT NULL DEFAULT 0,
    no_show_count       INT NOT NULL DEFAULT 0,
    avg_rating          DECIMAL(3,2) DEFAULT 0.00,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Platform metrics (CQRS read model)
CREATE TABLE platform_metric (
    metric_name         VARCHAR(100) PRIMARY KEY,
    metric_value        DECIMAL(15,2) NOT NULL DEFAULT 0,
    metric_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Pre-populate metric names
INSERT INTO platform_metric (metric_name, metric_value) VALUES
    ('total_registered_patients', 0),
    ('total_registered_doctors', 0),
    ('total_verified_doctors', 0),
    ('total_appointments_today', 0),
    ('total_appointments_this_month', 0),
    ('platform_revenue_today', 0.00);

-- Indexes
CREATE INDEX idx_daily_summary_date ON daily_appointment_summary(summary_date DESC);
CREATE INDEX idx_daily_summary_spec ON daily_appointment_summary(specialization);
```

### Key Java classes

**`AppointmentEventProjection.java`** — consumes appointment events, updates read models:
```java
@Component
public class AppointmentEventProjection {

    @KafkaListener(topics = "${mediq.kafka.topic.appointment-events}",
                   groupId = "mediq-analytics-appointment-group")
    @Transactional
    public void onAppointmentEvent(AppointmentEvent event, Acknowledgment ack) {
        // Each event type updates specific analytics tables
        switch (event.eventType()) {
            case "AppointmentBooked" -> {
                // Increment total_booked for today + specialization
                dailySummaryRepository.incrementBooked(
                    LocalDate.now(), event.specialization());
                platformMetricRepository.increment("total_appointments_today");
            }
            case "AppointmentConfirmed" -> {
                dailySummaryRepository.incrementConfirmed(
                    LocalDate.now(), event.specialization());
            }
            case "AppointmentCancelled" -> {
                dailySummaryRepository.incrementCancelled(
                    LocalDate.now(), event.specialization());
            }
            case "AppointmentCompleted" -> {
                dailySummaryRepository.incrementCompleted(
                    LocalDate.now(), event.specialization());
                doctorPerformanceRepository.incrementCompleted(event.doctorId());
            }
        }
        ack.acknowledge();
    }
}
```

**`UserEventProjection.java`** — consumes user events:
```java
@KafkaListener(topics = "${mediq.kafka.topic.user-events}",
               groupId = "mediq-analytics-user-group")
@Transactional
public void onUserEvent(UserEvent event, Acknowledgment ack) {
    switch (event.eventType()) {
        case "USER_REGISTERED" -> {
            if ("PATIENT".equals(event.userType())) {
                platformMetricRepository.increment("total_registered_patients");
            } else if ("DOCTOR".equals(event.userType())) {
                platformMetricRepository.increment("total_registered_doctors");
            }
        }
        case "DOCTOR_VERIFIED" -> {
            platformMetricRepository.increment("total_verified_doctors");
        }
    }
    ack.acknowledge();
}
```

**`AnalyticsDashboardController.java`**:
```
GET /analytics/dashboard
  → returns: {
      platformMetrics: { totalPatients, totalDoctors, totalAppointmentsToday },
      appointmentsByDay: [{ date, specialization, total }],
      topDoctors: [{ name, specialization, completedCount }]
    }
  → reads from 3 pre-computed tables, no joins, sub-10ms ✅

GET /analytics/appointments/daily?from=2024-01-01&to=2024-01-31
GET /analytics/doctors/performance
GET /analytics/platform/metrics
```

### application.properties for analytics-service
```properties
server.port=8087
spring.application.name=analytics-service
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediq_analytics}
spring.datasource.username=${DB_USERNAME:mediq}
spring.datasource.password=${DB_PASSWORD:mediq}
spring.flyway.schemas=mediq_analytics
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
mediq.kafka.topic.user-events=mediq.user.events
mediq.kafka.topic.appointment-events=mediq.appointment.events
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.tracing.sampling.probability=1.0
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

---

## TASK-M4c: Observability Stack

### Add Prometheus + Grafana + Loki to docker-compose.yml

```yaml
  # ── Prometheus — metrics scraping ─────────────────────────────────────────
  prometheus:
    image: prom/prometheus:v2.51.0
    container_name: mediq-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./observability/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./observability/alert-rules.yml:/etc/prometheus/alert-rules.yml
    networks:
      - mediq-net
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=7d'

  # ── Grafana — dashboards ───────────────────────────────────────────────────
  grafana:
    image: grafana/grafana:10.4.0
    container_name: mediq-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_DATASOURCES_DEFAULT_TYPE: prometheus
    volumes:
      - ./observability/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./observability/grafana/datasources:/etc/grafana/provisioning/datasources
    networks:
      - mediq-net
    depends_on:
      - prometheus

  # ── Loki — log aggregation ────────────────────────────────────────────────
  loki:
    image: grafana/loki:2.9.6
    container_name: mediq-loki
    ports:
      - "3100:3100"
    networks:
      - mediq-net

  # ── Promtail — log collector ──────────────────────────────────────────────
  promtail:
    image: grafana/promtail:2.9.6
    container_name: mediq-promtail
    volumes:
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      - ./observability/promtail.yml:/etc/promtail/config.yml
    networks:
      - mediq-net
    depends_on:
      - loki
```

### Prometheus config
`observability/prometheus.yml`:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alert-rules.yml"

scrape_configs:
  - job_name: 'user-service'
    static_configs:
      - targets: ['user-service:8081']
    metrics_path: '/actuator/prometheus'

  - job_name: 'doctor-service'
    static_configs:
      - targets: ['doctor-service:8083']
    metrics_path: '/actuator/prometheus'

  - job_name: 'appointment-service'
    static_configs:
      - targets: ['appointment-service:8084']
    metrics_path: '/actuator/prometheus'

  - job_name: 'notification-service'
    static_configs:
      - targets: ['notification-service:8085']
    metrics_path: '/actuator/prometheus'

  - job_name: 'emr-service'
    static_configs:
      - targets: ['emr-service:8086']
    metrics_path: '/actuator/prometheus'

  - job_name: 'analytics-service'
    static_configs:
      - targets: ['analytics-service:8087']
    metrics_path: '/actuator/prometheus'
```

### Alert rules
`observability/alert-rules.yml`:
```yaml
groups:
  - name: mediq-alerts
    rules:
      # High error rate — more than 5% of requests failing
      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          /
          rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High error rate on {{ $labels.application }}"
          description: "Error rate is {{ $value | humanizePercentage }}"

      # High p99 latency — requests taking more than 500ms
      - alert: HighLatency
        expr: |
          histogram_quantile(0.99,
            rate(http_server_requests_seconds_bucket[5m])) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High p99 latency on {{ $labels.application }}"
          description: "p99 latency is {{ $value }}s"

      # Kafka consumer lag growing — notification-service falling behind
      - alert: KafkaConsumerLagHigh
        expr: kafka_consumer_group_lag > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka consumer lag high for {{ $labels.consumergroup }}"

      # Service down — no metrics received
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"
```

### Grafana dashboard
`observability/grafana/dashboards/mediq-overview.json`:
Create a Grafana dashboard JSON with these panels:
```
Row 1: Platform Overview
  Panel 1: Request rate per service (line chart)
  Panel 2: Error rate per service (line chart)
  Panel 3: p99 latency per service (line chart)

Row 2: Kafka Health
  Panel 4: Consumer lag per consumer group (line chart)
  Panel 5: Messages produced per topic per minute
  Panel 6: Consumer group status (table)

Row 3: JVM Health
  Panel 7: JVM heap usage per service (line chart)
  Panel 8: GC pause time (line chart)
  Panel 9: Active DB connections per service

Row 4: Business Metrics (from analytics-service)
  Panel 10: Appointments booked today (stat)
  Panel 11: Active doctors (stat)
  Panel 12: Total patients (stat)
```

---

## TASK-M4d: BFF Pattern in KrakenD

### What BFF means
```
Backend For Frontend — different API shapes for different clients

Mobile app needs (limited screen, bandwidth):
  GET /api/mobile/doctors/search
  → returns ONLY: doctorId, name, specialization, fee, rating, nextAvailableSlot
  → 6 fields — lightweight

Web app needs (full screen, rich UI):
  GET /api/web/doctors/search
  → returns: doctorId, name, specialization, allSpecializations, fee,
             rating, reviewCount, clinicName, clinicAddress, availability,
             qualifications, languages, photos
  → 20+ fields — rich

Admin dashboard needs:
  GET /api/admin/doctors/search
  → returns: everything + verificationStatus + licenseExpiry + accountStatus
```

### KrakenD BFF configuration

Add to `krakend/partials/endpoint_doctors.tmpl`:

```json
{
  "comment": "Mobile BFF — lightweight doctor search",
  "endpoint": "/api/mobile/v1/doctors/search",
  "method": "GET",
  "backend": [{
    "url_pattern": "/doctors/search",
    "host": ["http://doctor-service:8083"],
    "allow": [
      "id", "fullName", "primarySpecialization",
      "consultationFee", "isVerified"
    ]
  }]
},
{
  "comment": "Web BFF — full doctor search with aggregation",
  "endpoint": "/api/web/v1/doctors/search",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/doctors/search",
      "host": ["http://doctor-service:8083"],
      "group": "doctorProfile"
    },
    {
      "url_pattern": "/doctors/{doctorId}/availability",
      "host": ["http://doctor-service:8083"],
      "group": "availability"
    }
  ]
},
{
  "comment": "Patient BFF — aggregated view: appointment + EMR summary",
  "endpoint": "/api/web/v1/patients/{patientId}/overview",
  "method": "GET",
  "extra_config": {
    "auth/validator": {
      "alg": "RS256",
      "jwk_url": "http://keycloak:8090/realms/mediq/protocol/openid-connect/certs"
    }
  },
  "backend": [
    {
      "url_pattern": "/users/{patientId}",
      "host": ["http://user-service:8081"],
      "group": "userProfile"
    },
    {
      "url_pattern": "/emr/patients/{patientId}/current",
      "host": ["http://emr-service:8086"],
      "group": "medicalSummary"
    },
    {
      "url_pattern": "/appointments?patientId={patientId}&limit=5",
      "host": ["http://appointment-service:8084"],
      "group": "recentAppointments"
    }
  ]
}
```

---

## Verification

### 1. Event Sourcing test
```powershell
# Add diagnosis to patient
curl -X POST http://localhost:8080/api/v1/emr/patients/{patientId}/events/diagnosis `
  -H "Content-Type: application/json" `
  -d '{"diagnoseCode":"E11","description":"Type 2 Diabetes","severity":"MODERATE"}'

# Add medication
curl -X POST http://localhost:8080/api/v1/emr/patients/{patientId}/events/medication `
  -H "Content-Type: application/json" `
  -d '{"medicationName":"Metformin","dosage":"500mg","frequency":"TWICE_DAILY"}'

# Get current state (from read model)
curl http://localhost:8080/api/v1/emr/patients/{patientId}/current

# Get full history (all events in order)
curl http://localhost:8080/api/v1/emr/patients/{patientId}/history

# Replay to specific date (time travel)
curl "http://localhost:8080/api/v1/emr/patients/{patientId}/as-of?date=2024-01-15"
```

### 2. Analytics CQRS test
```powershell
# Get dashboard (pre-computed, sub-10ms)
curl http://localhost:8080/api/v1/analytics/dashboard

# Get daily summary
curl "http://localhost:8080/api/v1/analytics/appointments/daily?from=2024-01-01&to=2024-01-31"
```

### 3. Grafana dashboard
```
Open http://localhost:3000
Login: admin/admin
Navigate to Dashboards → mediq-overview
Verify:
  - Request rate graphs showing data
  - p99 latency for each service
  - Kafka consumer lag panels
  - JVM heap usage
```

### 4. Alert testing
```powershell
# Trigger high error rate by stopping user-service
docker stop mediq-user-service

# Wait 2 minutes — ServiceDown alert should fire
# Check in Prometheus: http://localhost:9090/alerts

# Restart
docker start mediq-user-service
```

### 5. BFF response comparison
```powershell
# Mobile BFF — lightweight
curl http://localhost:8080/api/mobile/v1/doctors/search?verified=true
# Expected: minimal fields only

# Web BFF — full response with aggregation
curl http://localhost:8080/api/web/v1/doctors/search?verified=true
# Expected: doctorProfile + availability merged in one response
```

---

## Commit
```powershell
git add .
git commit -m "feat(m4): emr event sourcing, analytics CQRS, observability, BFF

- emr-service: Event Sourcing with immutable event store
  + patient state replay to any point in time
  + CQRS read model (patient_summary) for fast current state
- analytics-service: cross-service CQRS dashboard
  + consumes events from user, appointment services
  + pre-computed read models — sub-10ms dashboard queries
- Observability: Prometheus + Grafana + Loki + Promtail
  + 3 alert rules: error rate, latency, Kafka lag, service down
  + Grafana dashboard with business + technical metrics
- BFF pattern in KrakenD: mobile vs web vs admin response shapes
  + Patient overview BFF: aggregates user + EMR + appointments
- docker-compose: 14 services running end-to-end"
```

---

## Final State of mediq Platform

After Milestone 4, mediq has:

```
Services (9 business + 5 infra):
  user-service         8081  → identity, onboarding, Outbox pattern
  doctor-service       8083  → CQRS search, availability, event-driven
  appointment-service  8084  → Saga choreography, slot booking, Outbox
  notification-service 8085  → DLQ, idempotent delivery, multi-channel
  emr-service          8086  → Event Sourcing, medical records, time-travel
  analytics-service    8087  → CQRS cross-service dashboards
  krakend              8080  → API gateway, BFF patterns, JWT validation
  keycloak             8090  → Identity provider, JWT issuer
  postgres             5432  → 6 separate databases (one per service)
  redis                6379  → Cache, session store
  kafka                9092  → Event backbone
  jaeger               16686 → Distributed tracing UI
  prometheus           9090  → Metrics scraping
  grafana              3000  → Dashboards + alerts
  loki                 3100  → Log aggregation

Patterns implemented:
  ✅ Bounded Context (each service owns its domain)
  ✅ CQRS (doctor search + analytics dashboard)
  ✅ Saga Choreography (booking flow compensation)
  ✅ Outbox Pattern (reliable Kafka publishing)
  ✅ Event Sourcing (EMR medical records)
  ✅ Cache-Aside (Redis + fail-open)
  ✅ BFF Pattern (mobile vs web endpoints)
  ✅ DLQ (notification failure handling)
  ✅ Idempotency (notification deduplication)
  ✅ Distributed Tracing (OpenTelemetry + Jaeger)
  ✅ Liveness + Readiness Probes
  ✅ Kubernetes Manifests + Helm Chart
  ✅ KEDA (Kafka-lag-based autoscaling)
  ✅ Observability (Prometheus + Grafana + Loki)
```
