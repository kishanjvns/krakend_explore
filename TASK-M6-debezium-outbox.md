# mediq — Task M6: Outbox Pattern with Debezium CDC

## Branch
```powershell
git checkout main
git pull origin main
git checkout -b feature/mediq-m6-debezium-outbox
```

## Reference
- Lydtech blog: https://www.lydtechconsulting.com/blog/kafka-connect-debezium-demo
- Reference codebase: kafka-connect-debezium-postgres-1.0.0

## What This Replaces

```
DELETE:
  user-service/.../scheduler/UserOutboxRelayScheduler.java
  appointment-service/.../scheduler/OutboxRelayScheduler.java
  user-service/.../model/OutboxStatus.java
  appointment-service/.../model/OutboxStatus.java

ADD:
  debezium/connectors/  (5 connector JSON files)
  debezium/register-connectors.sh
  OutboxCleanupScheduler.java per service (7-day retention at 2am)

CHANGE:
  docker-compose.yml
    postgres: add wal_level=logical command args
    add 5 Kafka Connect containers (ports 8091-8095)
  scripts/postgres-init.sql
    add debezium user + GRANT statements
  Flyway migrations
    add destination + timestamp columns
    remove status + published_at columns
  UserOutboxEntity.java / AppointmentOutboxEntity.java
    remove status/published_at fields
    add destination/timestamp fields
  UserOutboxRepository.java / AppointmentOutboxRepository.java
    remove findByStatus method
    add deleteByCreatedAtBefore method
  UserService/AppointmentService
    set destination field when writing outbox entry
```

---

## STEP 1 — PostgreSQL WAL configuration

**File:** `docker-compose.yml`

Find the postgres service. Replace with:

```yaml
  postgres:
    image: postgres:16-alpine
    container_name: mediq-postgres
    command:
      - "postgres"
      - "-c"
      - "wal_level=logical"
      - "-c"
      - "max_replication_slots=10"
      - "-c"
      - "max_wal_senders=10"
      - "-c"
      - "wal_keep_size=1024"
    environment:
      POSTGRES_USER: mediq
      POSTGRES_PASSWORD: mediq
      POSTGRES_DB: mediq_users
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./scripts/postgres-init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - mediq-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U mediq -d mediq_users"]
      interval: 10s
      timeout: 5s
      retries: 5
```

> **WARNING:** If postgres container already has a data volume, delete it first:
> ```powershell
> docker compose down -v   # WARNING: deletes ALL existing data
> docker compose up
> ```

---

## STEP 2 — Debezium replication user

**File:** `scripts/postgres-init.sql` — add at end of existing file:

```sql
-- Debezium CDC replication user
CREATE USER debezium WITH REPLICATION LOGIN PASSWORD 'debezium';

GRANT CONNECT ON DATABASE mediq_users TO debezium;
GRANT CONNECT ON DATABASE mediq_doctors TO debezium;
GRANT CONNECT ON DATABASE mediq_appointments TO debezium;
GRANT CONNECT ON DATABASE mediq_payments TO debezium;
GRANT CONNECT ON DATABASE mediq_emr TO debezium;
```

---

## STEP 3 — Outbox schema migrations

### user-service — V3__debezium_outbox_migration.sql

Create `user-service/src/main/resources/db/migration/V3__debezium_outbox_migration.sql`:

```sql
ALTER TABLE mediq_users.user_outbox DROP COLUMN IF EXISTS status;
ALTER TABLE mediq_users.user_outbox DROP COLUMN IF EXISTS published_at;
DROP INDEX IF EXISTS mediq_users.idx_user_outbox_pending;

-- destination: Debezium EventRouter routes INSERT events to this Kafka topic
ALTER TABLE mediq_users.user_outbox
    ADD COLUMN IF NOT EXISTS destination VARCHAR(255)
    NOT NULL DEFAULT 'mediq.user.events';

-- timestamp: epoch millis, required by Debezium EventRouter
ALTER TABLE mediq_users.user_outbox
    ADD COLUMN IF NOT EXISTS timestamp BIGINT
    NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT * 1000;

CREATE INDEX IF NOT EXISTS idx_user_outbox_created_at
    ON mediq_users.user_outbox(created_at);

GRANT SELECT ON mediq_users.user_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_users TO debezium;
```

### appointment-service — V2__debezium_outbox_migration.sql

Create `appointment-service/src/main/resources/db/migration/V2__debezium_outbox_migration.sql`:

```sql
ALTER TABLE mediq_appointments.appointment_outbox DROP COLUMN IF EXISTS status;
ALTER TABLE mediq_appointments.appointment_outbox DROP COLUMN IF EXISTS published_at;
DROP INDEX IF EXISTS mediq_appointments.idx_outbox_pending;

ALTER TABLE mediq_appointments.appointment_outbox
    ADD COLUMN IF NOT EXISTS destination VARCHAR(255)
    NOT NULL DEFAULT 'mediq.appointment.events';

ALTER TABLE mediq_appointments.appointment_outbox
    ADD COLUMN IF NOT EXISTS timestamp BIGINT
    NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT * 1000;

CREATE INDEX IF NOT EXISTS idx_appointment_outbox_created_at
    ON mediq_appointments.appointment_outbox(created_at);

GRANT SELECT ON mediq_appointments.appointment_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_appointments TO debezium;
```

### payment-service — append to V1__create_payment_schema.sql

```sql
CREATE TABLE mediq_payments.service_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    destination     VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    timestamp       BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_outbox_created_at ON mediq_payments.service_outbox(created_at);
GRANT SELECT ON mediq_payments.service_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_payments TO debezium;
```

### doctor-service — V2__add_outbox.sql

```sql
CREATE TABLE mediq_doctors.service_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    destination     VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    timestamp       BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_doctor_outbox_created_at ON mediq_doctors.service_outbox(created_at);
GRANT SELECT ON mediq_doctors.service_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_doctors TO debezium;
```

### emr-service — V2__add_outbox.sql

```sql
CREATE TABLE mediq_emr.service_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    destination     VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    timestamp       BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_emr_outbox_created_at ON mediq_emr.service_outbox(created_at);
GRANT SELECT ON mediq_emr.service_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_emr TO debezium;
```

---

## STEP 4 — Delete relay schedulers and OutboxStatus enums

```powershell
Remove-Item user-service\src\main\java\com\mediq\scheduler\UserOutboxRelayScheduler.java
Remove-Item appointment-service\src\main\java\com\mediq\appointment\scheduler\OutboxRelayScheduler.java
Remove-Item user-service\src\main\java\com\mediq\model\OutboxStatus.java
Remove-Item appointment-service\src\main\java\com\mediq\appointment\model\OutboxStatus.java
```

---

## STEP 5 — Update UserOutboxEntity.java

Replace entire file `user-service/src/main/java/com/mediq/model/UserOutboxEntity.java`:

```java
package com.mediq.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_outbox", schema = "mediq_users")
public class UserOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType = "USER";

    @Column(name = "event_type", nullable = false)
    private String eventType;

    // Debezium EventRouter reads this field to route to correct Kafka topic
    // Value = full Kafka topic name e.g. "mediq.user.events"
    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    // Epoch milliseconds — required by Debezium EventRouter
    @Column(name = "timestamp", nullable = false)
    private long timestamp = Instant.now().toEpochMilli();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    // NO status — Debezium never updates outbox rows
    // NO published_at — not needed with CDC

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

---

## STEP 6 — Update AppointmentOutboxEntity.java

Replace entire file `appointment-service/src/main/java/com/mediq/appointment/model/AppointmentOutboxEntity.java`:

```java
package com.mediq.appointment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_outbox", schema = "mediq_appointments")
public class AppointmentOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType = "APPOINTMENT";

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "destination", nullable = false)
    private String destination = "mediq.appointment.events";

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "timestamp", nullable = false)
    private long timestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.timestamp = Instant.now().toEpochMilli();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

---

## STEP 7 — Update repositories

### UserOutboxRepository.java

```java
package com.mediq.repository;

import com.mediq.model.UserOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.UUID;

public interface UserOutboxRepository extends JpaRepository<UserOutboxEntity, UUID> {
    // Only for 7-day cleanup — Debezium reads WAL, never polls this table
    @Modifying
    @Query("DELETE FROM UserOutboxEntity o WHERE o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
```

### AppointmentOutboxRepository.java

```java
package com.mediq.appointment.repository;

import com.mediq.appointment.model.AppointmentOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.UUID;

public interface AppointmentOutboxRepository extends JpaRepository<AppointmentOutboxEntity, UUID> {
    @Modifying
    @Query("DELETE FROM AppointmentOutboxEntity o WHERE o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
```

---

## STEP 8 — Fix outbox writes in services

### user-service — wherever UserOutboxEntity is created

Search for `new UserOutboxEntity()` in UserEventPublisher.java or UserService.java.

Add these two lines and remove any `setStatus(OutboxStatus.PENDING)` line:

```java
outbox.setDestination("mediq.user.events");
outbox.setTimestamp(Instant.now().toEpochMilli());
// Remove: outbox.setStatus(OutboxStatus.PENDING);  ← delete this line
```

### appointment-service — wherever AppointmentOutboxEntity is created

Search for `new AppointmentOutboxEntity()` in AppointmentService.java.

Add if not present (default is already set in entity):
```java
outbox.setDestination("mediq.appointment.events");
// Remove: outbox.setStatus(OutboxStatus.PENDING);  ← delete this line
```

---

## STEP 9 — Add 7-day cleanup schedulers

### user-service — OutboxCleanupScheduler.java

Create `user-service/src/main/java/com/mediq/scheduler/OutboxCleanupScheduler.java`:

```java
package com.mediq.scheduler;

import com.mediq.repository.UserOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);
    private final UserOutboxRepository outboxRepository;

    public OutboxCleanupScheduler(UserOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // Runs every day at 2:00 AM
    // Deletes outbox rows older than 7 days
    // Data is already in Kafka — outbox is just a CDC relay buffer
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredOutboxEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteByCreatedAtBefore(cutoff);
        log.info("User outbox cleanup: deleted {} rows older than 7 days", deleted);
    }
}
```

### appointment-service — OutboxCleanupScheduler.java

Create `appointment-service/src/main/java/com/mediq/appointment/scheduler/OutboxCleanupScheduler.java`:

```java
package com.mediq.appointment.scheduler;

import com.mediq.appointment.repository.AppointmentOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);
    private final AppointmentOutboxRepository outboxRepository;

    public OutboxCleanupScheduler(AppointmentOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredOutboxEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Appointment outbox cleanup: deleted {} rows older than 7 days", deleted);
    }
}
```

---

## STEP 10 — Add 5 Kafka Connect containers to docker-compose.yml

Add AFTER the kafka service (use host ports 8091-8095 to avoid conflict with app services):

```yaml
  mediq-user-connect:
    image: quay.io/debezium/connect:2.7
    container_name: mediq-user-connect
    ports:
      - "8091:8083"
    environment:
      BOOTSTRAP_SERVERS: kafka:9092
      GROUP_ID: mediq-user-connect-group
      CONFIG_STORAGE_TOPIC: mediq.connect.user.configs
      OFFSET_STORAGE_TOPIC: mediq.connect.user.offsets
      STATUS_STORAGE_TOPIC: mediq.connect.user.status
      CONFIG_STORAGE_REPLICATION_FACTOR: "1"
      OFFSET_STORAGE_REPLICATION_FACTOR: "1"
      STATUS_STORAGE_REPLICATION_FACTOR: "1"
    networks:
      - mediq-net
    depends_on:
      kafka:
        condition: service_healthy
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8083/connectors"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s

  mediq-appointment-connect:
    image: quay.io/debezium/connect:2.7
    container_name: mediq-appointment-connect
    ports:
      - "8092:8083"
    environment:
      BOOTSTRAP_SERVERS: kafka:9092
      GROUP_ID: mediq-appointment-connect-group
      CONFIG_STORAGE_TOPIC: mediq.connect.appointment.configs
      OFFSET_STORAGE_TOPIC: mediq.connect.appointment.offsets
      STATUS_STORAGE_TOPIC: mediq.connect.appointment.status
      CONFIG_STORAGE_REPLICATION_FACTOR: "1"
      OFFSET_STORAGE_REPLICATION_FACTOR: "1"
      STATUS_STORAGE_REPLICATION_FACTOR: "1"
    networks:
      - mediq-net
    depends_on:
      kafka:
        condition: service_healthy
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8083/connectors"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s

  mediq-payment-connect:
    image: quay.io/debezium/connect:2.7
    container_name: mediq-payment-connect
    ports:
      - "8093:8083"
    environment:
      BOOTSTRAP_SERVERS: kafka:9092
      GROUP_ID: mediq-payment-connect-group
      CONFIG_STORAGE_TOPIC: mediq.connect.payment.configs
      OFFSET_STORAGE_TOPIC: mediq.connect.payment.offsets
      STATUS_STORAGE_TOPIC: mediq.connect.payment.status
      CONFIG_STORAGE_REPLICATION_FACTOR: "1"
      OFFSET_STORAGE_REPLICATION_FACTOR: "1"
      STATUS_STORAGE_REPLICATION_FACTOR: "1"
    networks:
      - mediq-net
    depends_on:
      kafka:
        condition: service_healthy
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8083/connectors"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s

  mediq-doctor-connect:
    image: quay.io/debezium/connect:2.7
    container_name: mediq-doctor-connect
    ports:
      - "8094:8083"
    environment:
      BOOTSTRAP_SERVERS: kafka:9092
      GROUP_ID: mediq-doctor-connect-group
      CONFIG_STORAGE_TOPIC: mediq.connect.doctor.configs
      OFFSET_STORAGE_TOPIC: mediq.connect.doctor.offsets
      STATUS_STORAGE_TOPIC: mediq.connect.doctor.status
      CONFIG_STORAGE_REPLICATION_FACTOR: "1"
      OFFSET_STORAGE_REPLICATION_FACTOR: "1"
      STATUS_STORAGE_REPLICATION_FACTOR: "1"
    networks:
      - mediq-net
    depends_on:
      kafka:
        condition: service_healthy
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8083/connectors"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s

  mediq-emr-connect:
    image: quay.io/debezium/connect:2.7
    container_name: mediq-emr-connect
    ports:
      - "8095:8083"
    environment:
      BOOTSTRAP_SERVERS: kafka:9092
      GROUP_ID: mediq-emr-connect-group
      CONFIG_STORAGE_TOPIC: mediq.connect.emr.configs
      OFFSET_STORAGE_TOPIC: mediq.connect.emr.offsets
      STATUS_STORAGE_TOPIC: mediq.connect.emr.status
      CONFIG_STORAGE_REPLICATION_FACTOR: "1"
      OFFSET_STORAGE_REPLICATION_FACTOR: "1"
      STATUS_STORAGE_REPLICATION_FACTOR: "1"
    networks:
      - mediq-net
    depends_on:
      kafka:
        condition: service_healthy
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8083/connectors"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s
```

---

## STEP 11 — Create connector JSON files

Create `debezium/connectors/` folder and the following 5 files.

Key config values explained:
- `route.by.field: "destination"` — reads the destination column
- `route.topic.replacement: "${routedByValue}"` — uses destination value directly as Kafka topic name
- `table.field.event.key: "aggregate_id"` — aggregate_id becomes the Kafka message key
- `table.field.event.payload: "payload"` — payload column becomes the Kafka message value
- `snapshot.mode: "when_needed"` — only re-snapshots if replication slot is missing (safe restarts)
- `slot.drop.on.stop: "false"` — keeps WAL position on stop (resume from exact LSN on restart)

### debezium/connectors/user-service-connector.json
```json
{
    "name": "mediq-user-outbox-connector",
    "config": {
        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
        "tasks.max": "1",
        "database.hostname": "postgres",
        "database.port": "5432",
        "database.user": "debezium",
        "database.password": "debezium",
        "database.dbname": "mediq_users",
        "table.include.list": "mediq_users.user_outbox",
        "plugin.name": "pgoutput",
        "slot.name": "mediq_user_outbox_slot",
        "publication.name": "mediq_user_outbox_pub",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.route.by.field": "destination",
        "transforms.outbox.route.topic.replacement": "${routedByValue}",
        "transforms.outbox.table.field.event.key": "aggregate_id",
        "transforms.outbox.table.field.event.payload": "payload",
        "transforms.outbox.table.field.event.timestamp": "timestamp",
        "tombstones.on.delete": "false",
        "heartbeat.interval.ms": "5000",
        "snapshot.mode": "when_needed",
        "slot.drop.on.stop": "false",
        "offset.flush.interval.ms": "5000",
        "topic.prefix": "mediq-users-db"
    }
}
```

### debezium/connectors/appointment-service-connector.json
```json
{
    "name": "mediq-appointment-outbox-connector",
    "config": {
        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
        "tasks.max": "1",
        "database.hostname": "postgres",
        "database.port": "5432",
        "database.user": "debezium",
        "database.password": "debezium",
        "database.dbname": "mediq_appointments",
        "table.include.list": "mediq_appointments.appointment_outbox",
        "plugin.name": "pgoutput",
        "slot.name": "mediq_appointment_outbox_slot",
        "publication.name": "mediq_appointment_outbox_pub",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.route.by.field": "destination",
        "transforms.outbox.route.topic.replacement": "${routedByValue}",
        "transforms.outbox.table.field.event.key": "aggregate_id",
        "transforms.outbox.table.field.event.payload": "payload",
        "transforms.outbox.table.field.event.timestamp": "timestamp",
        "tombstones.on.delete": "false",
        "heartbeat.interval.ms": "5000",
        "snapshot.mode": "when_needed",
        "slot.drop.on.stop": "false",
        "offset.flush.interval.ms": "5000",
        "topic.prefix": "mediq-appointments-db"
    }
}
```

### debezium/connectors/payment-service-connector.json
```json
{
    "name": "mediq-payment-outbox-connector",
    "config": {
        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
        "tasks.max": "1",
        "database.hostname": "postgres",
        "database.port": "5432",
        "database.user": "debezium",
        "database.password": "debezium",
        "database.dbname": "mediq_payments",
        "table.include.list": "mediq_payments.service_outbox",
        "plugin.name": "pgoutput",
        "slot.name": "mediq_payment_outbox_slot",
        "publication.name": "mediq_payment_outbox_pub",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.route.by.field": "destination",
        "transforms.outbox.route.topic.replacement": "${routedByValue}",
        "transforms.outbox.table.field.event.key": "aggregate_id",
        "transforms.outbox.table.field.event.payload": "payload",
        "transforms.outbox.table.field.event.timestamp": "timestamp",
        "tombstones.on.delete": "false",
        "heartbeat.interval.ms": "5000",
        "snapshot.mode": "when_needed",
        "slot.drop.on.stop": "false",
        "offset.flush.interval.ms": "5000",
        "topic.prefix": "mediq-payments-db"
    }
}
```

### debezium/connectors/doctor-service-connector.json
```json
{
    "name": "mediq-doctor-outbox-connector",
    "config": {
        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
        "tasks.max": "1",
        "database.hostname": "postgres",
        "database.port": "5432",
        "database.user": "debezium",
        "database.password": "debezium",
        "database.dbname": "mediq_doctors",
        "table.include.list": "mediq_doctors.service_outbox",
        "plugin.name": "pgoutput",
        "slot.name": "mediq_doctor_outbox_slot",
        "publication.name": "mediq_doctor_outbox_pub",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.route.by.field": "destination",
        "transforms.outbox.route.topic.replacement": "${routedByValue}",
        "transforms.outbox.table.field.event.key": "aggregate_id",
        "transforms.outbox.table.field.event.payload": "payload",
        "transforms.outbox.table.field.event.timestamp": "timestamp",
        "tombstones.on.delete": "false",
        "heartbeat.interval.ms": "5000",
        "snapshot.mode": "when_needed",
        "slot.drop.on.stop": "false",
        "offset.flush.interval.ms": "5000",
        "topic.prefix": "mediq-doctors-db"
    }
}
```

### debezium/connectors/emr-service-connector.json
```json
{
    "name": "mediq-emr-outbox-connector",
    "config": {
        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
        "tasks.max": "1",
        "database.hostname": "postgres",
        "database.port": "5432",
        "database.user": "debezium",
        "database.password": "debezium",
        "database.dbname": "mediq_emr",
        "table.include.list": "mediq_emr.service_outbox",
        "plugin.name": "pgoutput",
        "slot.name": "mediq_emr_outbox_slot",
        "publication.name": "mediq_emr_outbox_pub",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.route.by.field": "destination",
        "transforms.outbox.route.topic.replacement": "${routedByValue}",
        "transforms.outbox.table.field.event.key": "aggregate_id",
        "transforms.outbox.table.field.event.payload": "payload",
        "transforms.outbox.table.field.event.timestamp": "timestamp",
        "tombstones.on.delete": "false",
        "heartbeat.interval.ms": "5000",
        "snapshot.mode": "when_needed",
        "slot.drop.on.stop": "false",
        "offset.flush.interval.ms": "5000",
        "topic.prefix": "mediq-emr-db"
    }
}
```

---

## STEP 12 — Create register-connectors.sh

Create `debezium/register-connectors.sh`:

```bash
#!/bin/bash
CONNECTORS_DIR="$(dirname "$0")/connectors"

declare -A PORTS=(
    ["user-service-connector.json"]="8091"
    ["appointment-service-connector.json"]="8092"
    ["payment-service-connector.json"]="8093"
    ["doctor-service-connector.json"]="8094"
    ["emr-service-connector.json"]="8095"
)

echo "=== Registering Debezium connectors ==="

for file in "${!PORTS[@]}"; do
    PORT="${PORTS[$file]}"
    URL="http://localhost:${PORT}"
    FILEPATH="$CONNECTORS_DIR/$file"
    echo ""
    echo "--- $file (port $PORT) ---"

    until curl -sf "$URL/connectors" > /dev/null 2>&1; do
        echo "  Waiting for Kafka Connect..."
        sleep 5
    done

    NAME=$(python3 -c "import json; print(json.load(open('$FILEPATH'))['name'])")
    EXISTING=$(curl -sf "$URL/connectors/$NAME" 2>/dev/null)

    if [ -n "$EXISTING" ]; then
        CONFIG=$(python3 -c "import json; print(json.dumps(json.load(open('$FILEPATH'))['config']))")
        curl -sf -X PUT -H "Content-Type: application/json" -d "$CONFIG" "$URL/connectors/$NAME/config"
        echo "  Updated: $NAME"
    else
        curl -sf -X POST -H "Content-Type: application/json" -d @"$FILEPATH" "$URL/connectors"
        echo "  Created: $NAME"
    fi
done

echo ""
echo "=== Status ==="
for PORT in 8091 8092 8093 8094 8095; do
    echo "Port $PORT:"
    curl -sf "http://localhost:$PORT/connectors?expand=status" 2>/dev/null | \
      python3 -c "import sys,json; [print(f'  {n}: {i["status"]["connector"]["state"]}') for n,i in json.load(sys.stdin).items()]" 2>/dev/null || echo "  (not ready)"
done
```

Make executable:
```bash
# WSL
chmod +x debezium/register-connectors.sh
```

---

## Verification

### 1. Fresh start (required if postgres had a data volume)
```powershell
docker compose down -v
docker compose up --build
```

### 2. Verify WAL level set correctly
```powershell
docker exec -it mediq-postgres psql -U mediq -c "SHOW wal_level;"
# Expected: logical
```

### 3. Verify debezium user created
```powershell
docker exec -it mediq-postgres psql -U mediq -c "\du debezium"
# Expected: debezium | Replication
```

### 4. All connect containers healthy
```powershell
docker ps --format "table {{.Names}}\t{{.Status}}" | grep connect
# Expected: all 5 showing healthy
```

### 5. Register connectors (WSL)
```bash
wsl bash ./debezium/register-connectors.sh
```

### 6. All connectors RUNNING
```powershell
curl http://localhost:8091/connectors?expand=status | python3 -m json.tool
# state: RUNNING
```

### 7. Register a patient — observe CDC in action
```powershell
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{"firstName":"CDC","lastName":"Test","dateOfBirth":"1990-01-01","password":"Test@1234","contacts":[{"contactType":"EMAIL","contactValue":"cdc@test.com","isPrimary":true}]}'
```

### 8. Verify outbox row in DB
```powershell
docker exec -it mediq-postgres psql -U mediq -d mediq_users `
  -c "SELECT event_type, destination, created_at FROM mediq_users.user_outbox ORDER BY created_at DESC LIMIT 3;"
# Expected: USER_REGISTERED row with destination='mediq.user.events'
```

### 9. Verify Kafka received event (~200ms after insert)
```bash
# WSL
docker exec -it mediq-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic mediq.user.events \
  --from-beginning --max-messages 3
# Expected: UserEvent JSON published by Debezium
```

### 10. Compare latency
```powershell
docker logs mediq-notification-service | grep "OTP"
# With Debezium: OTP appears ~200ms after registration
# With old scheduler: OTP appeared up to 5000ms after registration
```

---

## Commit
```powershell
git add .
git commit -m "feat(m6): Debezium CDC replaces scheduler-based outbox

Infrastructure:
  PostgreSQL wal_level=logical (enables WAL logical decoding)
  5 dedicated Kafka Connect containers (ports 8091-8095)
  One container per service — complete isolation
  debezium/connectors/: 5 connector JSON files
  debezium/register-connectors.sh

Schema changes (all 5 services):
  Added destination column (Debezium EventRouter topic routing)
  Added timestamp column (epoch millis, required by EventRouter)
  Removed status column (Debezium never updates rows)
  Removed published_at column
  New outbox tables: payment, doctor, emr services

Application changes:
  Deleted UserOutboxRelayScheduler.java
  Deleted OutboxRelayScheduler.java
  Deleted OutboxStatus.java (user + appointment)
  Added OutboxCleanupScheduler.java (2am daily, 7-day retention)
  Updated entities: removed status/published_at, added destination/timestamp
  Updated repositories: removed findByStatus, added deleteByCreatedAtBefore
  Updated services: destination field set on outbox write

Result:
  Latency: 5000ms (scheduler poll) → ~200ms (WAL CDC)
  DB load: constant SELECT polling → zero idle load
  Kafka coupling: app had KafkaTemplate → app fully decoupled
  Isolation: dedicated connect per service, no shared bottleneck"
```
