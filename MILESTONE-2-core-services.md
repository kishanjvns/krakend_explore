# mediq — Milestone 2: Core Business Services

## Branch Strategy
```powershell
# Branch from Milestone 1 probes branch
git checkout feature/mediq-m1-probes-tracing
git checkout -b feature/mediq-m2-core-services
```

## What This Milestone Builds
```
TASK-M2a: doctor-service
  → Doctor profile, specialization, fee, availability
  → CQRS read model for doctor search
  → Consumes UserEvent (USER_REGISTERED, DOCTOR_VERIFIED)
  → Publishes DoctorEvent

TASK-M2b: appointment-service
  → Slot management + booking state machine
  → Saga choreography for booking flow
  → Consumes DoctorEvent + UserEvent

TASK-M2c: notification-service
  → Consumes events from all services
  → Multi-channel delivery (SMS placeholder, Email placeholder)
  → DLQ for failed notifications

TASK-M2d: Outbox Pattern retrofit on user-service
  → Replace fire-and-forget Kafka publish
  → Outbox table in same transaction as business data
  → Scheduler-based relay (Debezium in M3)
```

## New Services Created
```
mediq/
  user-service/          ← existing (M1)
  doctor-service/        ← NEW (M2a) — port 8083
  appointment-service/   ← NEW (M2b) — port 8084
  notification-service/  ← NEW (M2c) — port 8085
  referral-service/      ← existing (leave untouched)
```

---

## TASK-M2a: doctor-service

### Branch note
All tasks in this milestone share the same branch: `feature/mediq-m2-core-services`

### Package structure
```
com.mediq.doctor
  controller/
  dto/
  event/
  exception/
  health/
  model/
  repository/
  service/
  config/
```

### Database schema — doctor-service has its OWN PostgreSQL database
Database name: `mediq_doctors`

Flyway migration `V1__create_doctor_schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_doctors;

-- Doctor business profile (owned by doctor-service)
-- NOTE: user_id links to user-service but NO foreign key constraint
-- Cross-service referential integrity maintained via events, not DB constraints
CREATE TABLE doctor_profile (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE,        -- from user-service
    keycloak_id         VARCHAR(255),
    license_number      VARCHAR(100) NOT NULL UNIQUE,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    years_of_experience INT NOT NULL DEFAULT 0,
    consultation_fee    DECIMAL(10,2),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    is_verified         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Specializations (doctor can have multiple)
CREATE TABLE doctor_specialization (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL REFERENCES doctor_profile(id),
    specialization  VARCHAR(100) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Availability slots per day of week
CREATE TABLE doctor_availability (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL REFERENCES doctor_profile(id),
    day_of_week     VARCHAR(10) NOT NULL
                    CHECK (day_of_week IN ('MON','TUE','WED','THU','FRI','SAT','SUN')),
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    slot_duration   INT NOT NULL DEFAULT 30,   -- minutes per appointment
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- CQRS Read Model — pre-computed doctor search view
-- Updated by event handlers, NEVER by direct write path
CREATE TABLE doctor_search_view (
    id                  UUID PRIMARY KEY,             -- same as doctor_profile.id
    user_id             UUID NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    primary_specialization VARCHAR(100),
    all_specializations TEXT[],                       -- array for search
    consultation_fee    DECIMAL(10,2),
    years_of_experience INT,
    is_verified         BOOLEAN NOT NULL DEFAULT false,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    available_days      TEXT[],                       -- ['MON','WED','FRI']
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_doctor_profile_user_id ON doctor_profile(user_id);
CREATE INDEX idx_doctor_profile_verified ON doctor_profile(is_verified, is_active);
CREATE INDEX idx_doctor_specialization_doctor ON doctor_specialization(doctor_id);
CREATE INDEX idx_doctor_availability_doctor ON doctor_availability(doctor_id);
CREATE INDEX idx_doctor_search_specialization ON doctor_search_view(primary_specialization);
CREATE INDEX idx_doctor_search_verified ON doctor_search_view(is_verified, is_active);
CREATE INDEX idx_doctor_search_fee ON doctor_search_view(consultation_fee);
```

### Key Java classes to create

**`DoctorProfileEntity.java`** — JPA entity for `doctor_profile` table

**`DoctorSpecializationEntity.java`** — JPA entity for `doctor_specialization` table

**`DoctorAvailabilityEntity.java`** — JPA entity for `doctor_availability` table

**`DoctorSearchViewEntity.java`** — JPA entity for CQRS read model:
```java
// This entity is READ-ONLY from the write path perspective
// ONLY event handlers write to this table
// All search queries read from this table
@Entity
@Table(name = "doctor_search_view", schema = "mediq_doctors")
public class DoctorSearchViewEntity {
    @Id
    private UUID id;
    private UUID userId;
    private String fullName;
    private String primarySpecialization;
    @Array   // PostgreSQL array type
    private String[] allSpecializations;
    private BigDecimal consultationFee;
    private int yearsOfExperience;
    private boolean verified;
    private boolean active;
    @Array
    private String[] availableDays;
    private Instant updatedAt;
    // getters/setters
}
```

**`UserEventConsumer.java`** — consumes `mediq.user.events`:
```java
@KafkaListener(topics = "${mediq.kafka.topic.user-events}",
               groupId = "mediq-doctor-user-sync-group")
public void onUserEvent(UserEvent event, Acknowledgment ack) {
    switch (event.eventType()) {
        case "USER_REGISTERED" -> {
            if ("DOCTOR".equals(event.userType())) {
                // Create doctor_profile stub from user registration
                // PENDING state — no search view entry yet
                doctorService.createDoctorStub(event);
            }
        }
        case "DOCTOR_VERIFIED" -> {
            // Update doctor_profile.is_verified = true
            // Upsert doctor_search_view — now visible in search
            doctorService.activateDoctorInSearch(event);
        }
        case "USER_DEACTIVATED" -> {
            // Soft delete from search view — remove from results
            doctorService.deactivateDoctorFromSearch(event);
        }
        default -> log.debug("Ignoring: {}", event.eventType());
    }
    ack.acknowledge();
}
```

**`DoctorController.java`** — REST endpoints:
```
POST   /doctors/{doctorId}/specializations    ← add specialization
POST   /doctors/{doctorId}/availability       ← set availability slots
GET    /doctors/{doctorId}                    ← get doctor profile
GET    /doctors/{doctorId}/availability       ← get availability

GET    /doctors/search                        ← CQRS READ MODEL
  Query params:
    specialization=Cardiology
    minFee=500&maxFee=2000
    availableOn=MON
    verified=true
  → reads from doctor_search_view (pre-computed, no joins, fast)
```

**`DoctorEventPublisher.java`** — publishes to `mediq.doctor.events`:
```
DoctorProfileUpdated  — when doctor updates their info
DoctorAvailabilitySet — when doctor sets availability
```

### CQRS Implementation — The Key Pattern

```
WRITE PATH (doctor updates their profile):
  POST /doctors/{id}/specializations
  → INSERT doctor_specialization
  → UPDATE doctor_search_view (same transaction or async event)

READ PATH (patient searches for doctors):
  GET /doctors/search?specialization=Cardiology
  → SELECT * FROM doctor_search_view WHERE primary_specialization = 'Cardiology'
  → Single table, no joins, pre-computed → sub-10ms

WHY THIS IS CQRS:
  Write model: normalized (doctor_profile + doctor_specialization + doctor_availability)
  Read model:  denormalized (doctor_search_view — all in one row)
  Different schemas. Different data shapes. Same data.
```

### application.properties for doctor-service
```properties
server.port=8083
spring.application.name=doctor-service
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediq_doctors}
spring.datasource.username=${DB_USERNAME:mediq}
spring.datasource.password=${DB_PASSWORD:mediq}
spring.flyway.schemas=mediq_doctors
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
mediq.kafka.topic.user-events=mediq.user.events
mediq.kafka.topic.doctor-events=mediq.doctor.events
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.tracing.sampling.probability=1.0
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### Add doctor-service to docker-compose.yml
```yaml
  doctor-service:
    build:
      context: ./doctor-service
      dockerfile: Dockerfile
    container_name: mediq-doctor-service
    ports:
      - "8083:8083"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/mediq_doctors
      DB_USERNAME: mediq
      DB_PASSWORD: mediq
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JAEGER_ENDPOINT: http://jaeger:4318/v1/traces
    networks:
      - mediq-net
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      user-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
             "http://localhost:8083/actuator/health"]
      interval: 20s
      timeout: 5s
      retries: 5
      start_period: 45s
```

### Add PostgreSQL database for doctor-service
In docker-compose.yml, add init script to postgres service to create the database:
```yaml
  postgres:
    environment:
      POSTGRES_USER: mediq
      POSTGRES_PASSWORD: mediq
      POSTGRES_DB: mediq_users
      # Additional databases created via init script
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./scripts/postgres-init.sql:/docker-entrypoint-initdb.d/init.sql
```

Create `scripts/postgres-init.sql`:
```sql
-- Create all mediq databases
CREATE DATABASE mediq_users;
CREATE DATABASE mediq_doctors;
CREATE DATABASE mediq_appointments;
CREATE DATABASE mediq_notifications;
GRANT ALL PRIVILEGES ON DATABASE mediq_users TO mediq;
GRANT ALL PRIVILEGES ON DATABASE mediq_doctors TO mediq;
GRANT ALL PRIVILEGES ON DATABASE mediq_appointments TO mediq;
GRANT ALL PRIVILEGES ON DATABASE mediq_notifications TO mediq;
```

---

## TASK-M2b: appointment-service

### Package
`com.mediq.appointment`

### Port: 8084
### Database: `mediq_appointments`

### Flyway migration `V1__create_appointment_schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_appointments;

-- Patient projection (copied from user events — no cross-service join)
CREATE TABLE patient_projection (
    user_id     UUID PRIMARY KEY,
    full_name   VARCHAR(200) NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(20),
    is_active   BOOLEAN NOT NULL DEFAULT true,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Doctor projection (copied from doctor events)
CREATE TABLE doctor_projection (
    doctor_id       UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    specialization  VARCHAR(100),
    is_verified     BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Appointment slots
CREATE TABLE appointment_slot (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL,
    slot_date       DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                    CHECK (status IN ('AVAILABLE','BOOKED','BLOCKED','CANCELLED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (doctor_id, slot_date, start_time)   -- no double booking
);

-- Appointment bookings (state machine)
CREATE TABLE appointment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id         UUID NOT NULL REFERENCES appointment_slot(id),
    patient_id      UUID NOT NULL,
    doctor_id       UUID NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT'
                    CHECK (status IN (
                        'PENDING_PAYMENT',
                        'PAYMENT_FAILED',
                        'CONFIRMED',
                        'CANCELLED',
                        'COMPLETED',
                        'NO_SHOW'
                    )),
    booked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Outbox table for reliable event publishing (Saga events)
CREATE TABLE appointment_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,       -- appointment.id
    aggregate_type  VARCHAR(50) NOT NULL DEFAULT 'APPOINTMENT',
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

-- Indexes
CREATE INDEX idx_slot_doctor_date ON appointment_slot(doctor_id, slot_date);
CREATE INDEX idx_slot_status ON appointment_slot(status);
CREATE INDEX idx_appointment_patient ON appointment(patient_id);
CREATE INDEX idx_appointment_doctor ON appointment(doctor_id);
CREATE INDEX idx_appointment_status ON appointment(status);
CREATE INDEX idx_outbox_pending ON appointment_outbox(status, created_at)
    WHERE status = 'PENDING';
```

### Appointment State Machine

```
States:
  PENDING_PAYMENT  → patient booked, waiting for payment
  PAYMENT_FAILED   → payment service reported failure → slot released
  CONFIRMED        → payment successful → appointment locked
  CANCELLED        → patient/doctor cancelled before appointment
  COMPLETED        → appointment happened
  NO_SHOW          → patient didn't show up

Transitions:
  BookAppointment    → PENDING_PAYMENT  + publishes AppointmentBooked
  PaymentCompleted   → CONFIRMED        + publishes AppointmentConfirmed
  PaymentFailed      → PAYMENT_FAILED   + publishes AppointmentCancelled + slot released
  CancelAppointment  → CANCELLED        + publishes AppointmentCancelled + slot released
  CompleteVisit      → COMPLETED        + publishes AppointmentCompleted
  MarkNoShow         → NO_SHOW          + publishes NoShowRecorded
```

### Saga Choreography Flow

```
Patient books appointment:

1. appointment-service:
   → checks slot available (SELECT FOR UPDATE on slot — prevents race condition)
   → creates appointment (status=PENDING_PAYMENT)
   → writes AppointmentBooked to outbox table
   → updates slot status to BOOKED
   → all in ONE @Transactional

2. Outbox relay (scheduler) reads outbox:
   → publishes AppointmentBooked to mediq.appointment.events

3. payment-service (future M3) consumes AppointmentBooked:
   → charges patient
   → publishes PaymentCompleted OR PaymentFailed to mediq.payment.events

4a. appointment-service consumes PaymentCompleted:
    → updates appointment status=CONFIRMED
    → writes AppointmentConfirmed to outbox
    → releases slot only if rebooking — slot stays BOOKED

4b. appointment-service consumes PaymentFailed:
    → updates appointment status=PAYMENT_FAILED
    → updates slot status back to AVAILABLE  ← compensation
    → writes AppointmentCancelled to outbox

5. notification-service consumes AppointmentConfirmed:
   → sends SMS "Your appointment with Dr. X is confirmed"
   → sends email confirmation

Compensation (payment failed):
  AppointmentBooked published → PaymentFailed received
  → appointment cancelled, slot released back to AVAILABLE
  → AppointmentCancelled published → notification sent
```

### Key Java classes

**`AppointmentEntity.java`** — JPA entity with status enum

**`AppointmentSlotEntity.java`** — JPA entity

**`AppointmentOutboxEntity.java`** — outbox table entity

**`AppointmentService.java`**:
```java
@Transactional
public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
    // Step 1: Lock the slot (SELECT FOR UPDATE prevents race condition)
    AppointmentSlotEntity slot = slotRepository
        .findByIdForUpdate(request.slotId())  // uses PESSIMISTIC_WRITE lock
        .orElseThrow(() -> new SlotNotFoundException(request.slotId()));

    if (slot.getStatus() != SlotStatus.AVAILABLE) {
        throw new SlotNotAvailableException(request.slotId());
    }

    // Step 2: Create appointment
    AppointmentEntity appointment = new AppointmentEntity();
    appointment.setSlot(slot);
    appointment.setPatientId(request.patientId());
    appointment.setDoctorId(slot.getDoctorId());
    appointment.setStatus(AppointmentStatus.PENDING_PAYMENT);
    appointmentRepository.save(appointment);

    // Step 3: Mark slot as BOOKED
    slot.setStatus(SlotStatus.BOOKED);
    slotRepository.save(slot);

    // Step 4: Write to outbox (same transaction — reliable event publishing)
    AppointmentOutboxEntity outbox = new AppointmentOutboxEntity();
    outbox.setAggregateId(appointment.getId());
    outbox.setEventType("AppointmentBooked");
    outbox.setPayload(buildPayload(appointment));
    outboxRepository.save(outbox);

    // All 4 steps are ONE transaction — atomic
    // If any step fails → everything rolled back → no orphaned data
    return appointmentMapper.toResponse(appointment);
}
```

**`OutboxRelayScheduler.java`** — polls outbox and publishes to Kafka:
```java
@Component
public class OutboxRelayScheduler {

    // Runs every 5 seconds
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayPendingEvents() {
        List<AppointmentOutboxEntity> pending =
            outboxRepository.findByStatus(OutboxStatus.PENDING);

        for (AppointmentOutboxEntity event : pending) {
            try {
                kafkaTemplate.send(
                    "mediq.appointment.events",
                    event.getAggregateId().toString(),
                    event.getPayload()
                ).get(5, TimeUnit.SECONDS); // synchronous wait for this relay

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Outbox relay failed for eventId={}: {}",
                    event.getId(), e.getMessage());
                event.setStatus(OutboxStatus.FAILED);
                outboxRepository.save(event);
                // Will be retried on next poll if reset to PENDING
                // Or handled by dead-letter process
            }
        }
    }
}
```

### application.properties for appointment-service
```properties
server.port=8084
spring.application.name=appointment-service
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediq_appointments}
spring.datasource.username=${DB_USERNAME:mediq}
spring.datasource.password=${DB_PASSWORD:mediq}
spring.flyway.schemas=mediq_appointments
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
mediq.kafka.topic.user-events=mediq.user.events
mediq.kafka.topic.doctor-events=mediq.doctor.events
mediq.kafka.topic.appointment-events=mediq.appointment.events
spring.scheduling.enabled=true
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.tracing.sampling.probability=1.0
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### Add to docker-compose.yml
```yaml
  appointment-service:
    build:
      context: ./appointment-service
      dockerfile: Dockerfile
    container_name: mediq-appointment-service
    ports:
      - "8084:8084"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/mediq_appointments
      DB_USERNAME: mediq
      DB_PASSWORD: mediq
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      JAEGER_ENDPOINT: http://jaeger:4318/v1/traces
    networks:
      - mediq-net
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
             "http://localhost:8084/actuator/health"]
      interval: 20s
      timeout: 5s
      retries: 5
      start_period: 45s
```

---

## TASK-M2c: notification-service

### Package
`com.mediq.notification`

### Port: 8085
### Database: `mediq_notifications`

### Flyway migration `V1__create_notification_schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_notifications;

-- All outbound notifications
CREATE TABLE notification (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id   UUID NOT NULL,
    recipient_email     VARCHAR(255),
    recipient_phone     VARCHAR(20),
    channel             VARCHAR(10) NOT NULL
                        CHECK (channel IN ('EMAIL','SMS','PUSH')),
    notification_type   VARCHAR(100) NOT NULL,  -- APPOINTMENT_CONFIRMED, WELCOME, etc.
    subject             VARCHAR(500),
    body                TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SENT','FAILED')),
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,  -- prevents duplicate sends
    retry_count         INT NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ
);

-- DLQ — failed notifications after max retries
CREATE TABLE notification_dlq (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_id         UUID NOT NULL REFERENCES notification(id),
    event_type          VARCHAR(100),
    event_payload       JSONB,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_notification_user ON notification(recipient_user_id);
CREATE INDEX idx_notification_status ON notification(status);
CREATE INDEX idx_notification_idempotency ON notification(idempotency_key);
```

### Key Java classes

**`AppointmentEventConsumer.java`** — consumes `mediq.appointment.events`:
```java
@KafkaListener(topics = "${mediq.kafka.topic.appointment-events}",
               groupId = "mediq-notification-appointment-group")
public void onAppointmentEvent(AppointmentEvent event, Acknowledgment ack) {
    // idempotency key = eventId from the source event
    // prevents sending duplicate SMS if Kafka delivers twice
    String idempotencyKey = event.eventId() + ":notification";

    try {
        switch (event.eventType()) {
            case "AppointmentConfirmed" -> notificationService.sendAppointmentConfirmation(event, idempotencyKey);
            case "AppointmentCancelled" -> notificationService.sendCancellationNotice(event, idempotencyKey);
            default -> log.debug("No notification for: {}", event.eventType());
        }
        ack.acknowledge();
    } catch (Exception e) {
        log.error("Notification failed for event={}: {}", event.eventType(), e.getMessage());
        // Do NOT ack — Kafka retries
        // After max retries → DefaultErrorHandler sends to DLQ topic
    }
}
```

**`UserEventConsumer.java`** — consumes `mediq.user.events`:
```java
// Sends welcome notification on USER_REGISTERED
// Sends verification status notification on DOCTOR_VERIFIED
```

**`NotificationService.java`**:
```java
@Transactional
public void sendAppointmentConfirmation(AppointmentEvent event, String idempotencyKey) {
    // Idempotency check — never send twice
    if (notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
        log.info("Notification already sent for key={} — skipping", idempotencyKey);
        return;
    }

    NotificationEntity notification = new NotificationEntity();
    notification.setRecipientUserId(event.patientId());
    notification.setChannel(Channel.SMS);
    notification.setNotificationType("APPOINTMENT_CONFIRMED");
    notification.setBody(buildConfirmationBody(event));
    notification.setIdempotencyKey(idempotencyKey);
    notification.setStatus(NotificationStatus.PENDING);
    notificationRepository.save(notification);

    // Send via channel (SMS stub for now — real integration in M4)
    smsGateway.send(notification.getRecipientPhone(), notification.getBody());

    notification.setStatus(NotificationStatus.SENT);
    notification.setSentAt(Instant.now());
    notificationRepository.save(notification);
}
```

**`SmsGateway.java`** — stub implementation:
```java
@Component
public class SmsGateway {
    private static final Logger log = LoggerFactory.getLogger(SmsGateway.class);

    public void send(String phone, String message) {
        // TODO: integrate with actual SMS provider (Twilio, MSG91) in M4
        // For now: log only — simulates successful send
        log.info("SMS STUB → phone={} message={}", phone, message);
    }
}
```

**Kafka DLQ configuration** in `NotificationKafkaConfig.java`:
```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new TopicPartition(
                record.topic() + ".DLQ", record.partition()));

    ExponentialBackOffWithMaxRetries backoff =
        new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(1000L);
    backoff.setMultiplier(2.0);

    return new DefaultErrorHandler(recoverer, backoff);
}
```

### application.properties for notification-service
```properties
server.port=8085
spring.application.name=notification-service
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediq_notifications}
spring.datasource.username=${DB_USERNAME:mediq}
spring.datasource.password=${DB_PASSWORD:mediq}
spring.flyway.schemas=mediq_notifications
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
mediq.kafka.topic.user-events=mediq.user.events
mediq.kafka.topic.appointment-events=mediq.appointment.events
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.tracing.sampling.probability=1.0
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### Add to docker-compose.yml
```yaml
  notification-service:
    build:
      context: ./notification-service
      dockerfile: Dockerfile
    container_name: mediq-notification-service
    ports:
      - "8085:8085"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/mediq_notifications
      DB_USERNAME: mediq
      DB_PASSWORD: mediq
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      JAEGER_ENDPOINT: http://jaeger:4318/v1/traces
    networks:
      - mediq-net
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
             "http://localhost:8085/actuator/health"]
      interval: 20s
      timeout: 5s
      retries: 5
      start_period: 45s
```

---

## TASK-M2d: Outbox Pattern — Retrofit user-service

### Why
```
Current user-service problem:
  @Transactional save(user) → DB committed ✅
  kafkaTemplate.send(event) → can fail silently ❌
  User created in DB but NO downstream knows about it

Fix: Outbox pattern
  Step 1: save(user) + save(outbox_event) in ONE transaction
  Step 2: Scheduler reads outbox → publishes to Kafka → marks PUBLISHED
  If Kafka fails → outbox entry stays PENDING → retried next poll
  Guaranteed: event published exactly once (idempotent consumer handles duplicates)
```

### Changes to user-service

**Add to `V1__create_user_schema.sql`** — add after existing tables:

```sql
-- Outbox for reliable Kafka publishing
CREATE TABLE user_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,   -- user_id
    aggregate_type  VARCHAR(50) NOT NULL DEFAULT 'USER',
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_user_outbox_pending ON user_outbox(status, created_at)
    WHERE status = 'PENDING';
```

**Create `UserOutboxEntity.java`** in `com.mediq.model`:
```java
@Entity
@Table(name = "user_outbox", schema = "mediq_users")
public class UserOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String aggregateId;
    private String aggregateType = "USER";
    private String eventType;
    @Column(columnDefinition = "jsonb")
    private String payload;     // JSON string of UserEvent
    @Enumerated(EnumType.STRING)
    private OutboxStatus status = OutboxStatus.PENDING;
    private Instant createdAt = Instant.now();
    private Instant publishedAt;
    // getters/setters
}
```

**Modify `UserService.java`** — replace `eventPublisher.publish(event)` calls:

```java
// BEFORE (fire-and-forget):
eventPublisher.publish(event);

// AFTER (outbox — same transaction as user save):
UserOutboxEntity outbox = new UserOutboxEntity();
outbox.setAggregateId(user.getId().toString());
outbox.setEventType(event.eventType());
outbox.setPayload(objectMapper.writeValueAsString(event));
outboxRepository.save(outbox);
// Kafka publish happens in scheduler — NOT here
```

**Create `UserOutboxRelayScheduler.java`**:
```java
@Component
public class UserOutboxRelayScheduler {

    @Scheduled(fixedDelay = 5000) // every 5 seconds
    @Transactional
    public void relay() {
        outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
            .forEach(event -> {
                try {
                    UserEvent userEvent = objectMapper.readValue(
                        event.getPayload(), UserEvent.class);
                    kafkaTemplate.send(
                        topic,
                        userEvent.userId(),
                        userEvent
                    ).get(5, TimeUnit.SECONDS);

                    event.setStatus(OutboxStatus.PUBLISHED);
                    event.setPublishedAt(Instant.now());
                    outboxRepository.save(event);
                    log.info("Outbox published eventType={} userId={}",
                        event.getEventType(), event.getAggregateId());
                } catch (Exception e) {
                    log.error("Outbox relay failed: {}", e.getMessage());
                    event.setStatus(OutboxStatus.FAILED);
                    outboxRepository.save(event);
                }
            });
    }
}
```

Add to `application.properties`:
```properties
spring.scheduling.enabled=true
```

---

## KrakenD — Add New Service Routes

Update `krakend/settings/hosts.json`:
```json
{
  "user_service": ["http://user-service:8081"],
  "doctor_service": ["http://doctor-service:8083"],
  "appointment_service": ["http://appointment-service:8084"],
  "referral_service": ["http://referral-service:8082"]
}
```

Create `krakend/partials/endpoint_doctors.tmpl`:
```json
{
  "endpoint": "/api/v1/doctors/search",
  "method": "GET",
  "backend": [{
    "url_pattern": "/doctors/search",
    "host": ["http://doctor-service:8083"]
  }]
},
{
  "endpoint": "/api/v1/doctors/{doctorId}",
  "method": "GET",
  "extra_config": {{ include "auth_doctor_admin.tmpl" }},
  "backend": [{
    "url_pattern": "/doctors/{doctorId}",
    "host": ["http://doctor-service:8083"]
  }]
},
{
  "endpoint": "/api/v1/doctors/{doctorId}/availability",
  "method": "POST",
  "extra_config": {{ include "auth_doctor_admin.tmpl" }},
  "backend": [{
    "url_pattern": "/doctors/{doctorId}/availability",
    "host": ["http://doctor-service:8083"]
  }]
}
```

Create `krakend/partials/endpoint_appointments.tmpl`:
```json
{
  "endpoint": "/api/v1/appointments",
  "method": "POST",
  "extra_config": {{ include "auth_doctor_nurse_admin.tmpl" }},
  "backend": [{
    "url_pattern": "/appointments",
    "host": ["http://appointment-service:8084"]
  }]
},
{
  "endpoint": "/api/v1/appointments/{appointmentId}",
  "method": "GET",
  "extra_config": {{ include "auth_doctor_nurse_admin.tmpl" }},
  "backend": [{
    "url_pattern": "/appointments/{appointmentId}",
    "host": ["http://appointment-service:8084"]
  }]
}
```

---

## Verification

### 1. Build all services
```powershell
cd D:\codebase\krakend_explore
# Build each service
cd doctor-service && mvn clean package -DskipTests && cd ..
cd appointment-service && mvn clean package -DskipTests && cd ..
cd notification-service && mvn clean package -DskipTests && cd ..
```

### 2. Start everything
```powershell
docker compose up --build
# Wait for all 10 services to be healthy
```

### 3. End-to-end flow test

```powershell
# Step 1: Register a doctor (user-service)
curl -X POST http://localhost:8080/api/v1/users/doctors/register `
  -H "Content-Type: application/json" `
  -d '{"firstName":"Priya","lastName":"Verma","dateOfBirth":"1985-08-20",
       "password":"Test@1234","licenseNumber":"MCI-2024-98765",
       "licenseExpiry":"2027-12-31","yearsOfExperience":10,
       "contacts":[{"contactType":"EMAIL","contactValue":"dr.priya@mediq.com","isPrimary":true}]}'

# Step 2: Verify doctor via admin (user-service)
# (get the doctorUserId from Step 1 response)
curl -X PUT http://localhost:8080/api/v1/users/doctors/{doctorUserId}/verify `
  -H "Content-Type: application/json" `
  -H "X-User-Role: ADMIN" `
  -H "X-User-Id: {adminId}" `
  -d '{"status":"VERIFIED"}'

# Step 3: Check doctor appears in search (doctor-service CQRS read model)
# Wait ~5 seconds for event propagation
curl http://localhost:8080/api/v1/doctors/search?verified=true

# Step 4: Register a patient
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{"firstName":"Rahul","lastName":"Sharma","dateOfBirth":"1990-05-15",
       "password":"Test@1234",
       "contacts":[{"contactType":"EMAIL","contactValue":"rahul@example.com","isPrimary":true}]}'

# Step 5: Check Kafka topics have events
docker exec -it mediq-kafka kafka-topics --list --bootstrap-server localhost:9092
# Expected topics:
# mediq.user.events
# mediq.doctor.events
# mediq.appointment.events
# mediq.appointment.events.DLQ (auto-created if any failures)

# Step 6: Check outbox in user-service DB
docker exec -it mediq-postgres psql -U mediq -d mediq_users
mediq_users=# SELECT event_type, status, created_at FROM mediq_users.user_outbox ORDER BY created_at DESC LIMIT 5;
```

### 4. Jaeger traces
```
http://localhost:16686
Check traces for:
  user-service → doctor-service event flow
  appointment-service → notification-service event flow
```

---

## Commit
```powershell
git add .
git commit -m "feat(m2): add doctor-service, appointment-service, notification-service

- doctor-service: CQRS read model for doctor search
- appointment-service: Saga choreography + slot booking with SELECT FOR UPDATE
- notification-service: idempotent notification delivery + DLQ
- user-service: Outbox pattern replaces fire-and-forget Kafka publish
- All services: own PostgreSQL DB, own Kafka consumer group
- KrakenD: new routes for doctor and appointment endpoints
- docker-compose: 10 services running"
```
