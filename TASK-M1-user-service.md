# mediq — Task M1: user-service
## Branch Strategy
```powershell
# Run from D:\codebase\krakend_explore
git checkout dev/krakend-step6-auth-keycloak
git checkout -b feature/mediq-m1-user-service
```

---

## Context

You are refactoring the existing `patient-service` into `user-service` as part of the **mediq** platform — a Practo-like healthcare system.

**What you are building:**
- user-service owns onboarding of Patient, Doctor, Admin
- It stores business profile (name, dob, address, contact)
- It publishes domain events to Kafka on every state change
- A Keycloak sync consumer reads those events and creates identities in Keycloak asynchronously
- Other services (appointment, notification, doctor) will consume user events and maintain their own projections

**What you are NOT building in this task:**
- Authentication (Keycloak handles that)
- Appointment booking
- Doctor specialization or availability
- Notifications

**Existing code to KEEP (do not delete):**
- `krakend/` directory — all KrakenD config stays
- `keycloak/` directory — realm config stays, you will UPDATE it
- `docker-compose.yml` — you will EXTEND it
- `referral-service/` — leave untouched for now

**Existing code to REFACTOR:**
- `patient-service/` → rename folder to `user-service`
- All `com.trucare` packages → `com.mediq`
- All `patient-service` references → `user-service`

---

## Architecture Decisions Already Made

```
user-service DB:       PostgreSQL (own schema: mediq_users)
Event backbone:        Kafka (topic: mediq.user.events)
Keycloak sync:         Event-driven — separate consumer reads Kafka
Cache:                 Redis (key: user:{userId}, TTL: 30 min)
Auth boundary:         Keycloak owns credentials, user-service owns profile
Inter-service reads:   Other services consume events, store own projections
Package root:          com.mediq
Port:                  8081
```

---

## Database Schema

Create these tables in PostgreSQL schema `mediq_users`:

```sql
-- Core user identity (Patient, Doctor, Admin)
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id         VARCHAR(255) UNIQUE,          -- set after Keycloak sync
    user_type           VARCHAR(20) NOT NULL           -- PATIENT, DOCTOR, ADMIN
                        CHECK (user_type IN ('PATIENT','DOCTOR','ADMIN')),
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    date_of_birth       DATE,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    is_verified         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID
);

-- Addresses (HOME, WORK, BILLING)
CREATE TABLE user_address (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    address_type    VARCHAR(20) NOT NULL
                    CHECK (address_type IN ('HOME','WORK','BILLING')),
    address_line1   VARCHAR(255) NOT NULL,
    address_line2   VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    country         VARCHAR(100) NOT NULL DEFAULT 'India',
    zip             VARCHAR(20) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Contact info (EMAIL, PHONE)
CREATE TABLE user_contact (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    contact_type    VARCHAR(10) NOT NULL
                    CHECK (contact_type IN ('EMAIL','PHONE')),
    contact_value   VARCHAR(255) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    is_verified     BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Doctor-specific identity data
CREATE TABLE doctor_profile (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL UNIQUE REFERENCES users(id),
    license_number       VARCHAR(100) NOT NULL UNIQUE,
    license_expiry       DATE NOT NULL,
    years_of_experience  INT NOT NULL DEFAULT 0,
    verification_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED')),
    verified_by          UUID,                         -- Admin user_id
    verified_at          TIMESTAMPTZ,
    rejection_reason     VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_keycloak_id ON users(keycloak_id);
CREATE INDEX idx_user_address_user_id ON user_address(user_id);
CREATE INDEX idx_user_contact_user_id ON user_contact(user_id);
CREATE INDEX idx_doctor_profile_user_id ON doctor_profile(user_id);
CREATE INDEX idx_doctor_profile_verification ON doctor_profile(verification_status);
```

---

## Step-by-Step Implementation

---

### Step 0: Rename and Restructure

> 🤔 **Before starting — think about this:**
> You are renaming `patient-service` to `user-service` and changing all packages from `com.trucare` to `com.mediq`. What files will need changing beyond just Java files? List them before you start.
> *(Hint: think about Docker, Maven, application properties, and KrakenD config)*

**Actions:**
1. Copy `patient-service/` to `user-service/` (do not delete original yet)
2. In `user-service/pom.xml`:
   - Change `groupId` to `com.mediq`
   - Change `artifactId` to `user-service`
   - Change `description` to `mediq User Service`
3. Rename all Java packages from `com.trucare` → `com.mediq`
4. Rename main class to `UserServiceApplication`
5. In `user-service/Dockerfile`:
   - Change JAR name to `user-service-1.0.0.jar`
   - Change EXPOSE to `8081`
6. In `user-service/src/main/resources/application.properties`:
   - Change `spring.application.name=user-service`
   - Change `logging.level.com.mediq=DEBUG`

---

### Step 1: Dependencies — pom.xml

> 🤔 **Before implementing — think about this:**
> This service needs PostgreSQL, JPA, Kafka, Redis, and Flyway. Why Flyway specifically? What problem does it solve that running raw SQL scripts manually does not?

Replace the existing `pom.xml` dependencies section with:

```xml
<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- JPA + Hibernate -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Flyway — DB migrations -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Validation (jakarta) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Jackson Java Time -->
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

### Step 2: Application Properties

> 🤔 **Before implementing — think about this:**
> We are using environment variables (e.g., `${DB_URL}`) instead of hardcoded values. Why? What problem does hardcoding `localhost:5432` in properties cause when this service runs in Docker or Kubernetes?

Replace `src/main/resources/application.properties` entirely:

```properties
# ── Server ────────────────────────────────────────────────────────────────────
server.port=8081
spring.application.name=user-service

# ── Database ──────────────────────────────────────────────────────────────────
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediq_users}
spring.datasource.username=${DB_USERNAME:mediq}
spring.datasource.password=${DB_PASSWORD:mediq}
spring.datasource.driver-class-name=org.postgresql.Driver

# Connection pool (HikariCP — Spring Boot default)
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=20000

# ── JPA ───────────────────────────────────────────────────────────────────────
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false

# ── Flyway ────────────────────────────────────────────────────────────────────
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.schemas=mediq_users

# ── Kafka ─────────────────────────────────────────────────────────────────────
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3
spring.kafka.producer.properties.enable.idempotence=true

# ── Redis ─────────────────────────────────────────────────────────────────────
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.timeout=2000ms

# ── Actuator ──────────────────────────────────────────────────────────────────
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
management.health.redis.enabled=true
management.health.db.enabled=true

# ── Jackson ───────────────────────────────────────────────────────────────────
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.deserialization.fail-on-unknown-properties=false

# ── Logging ───────────────────────────────────────────────────────────────────
logging.level.com.mediq=DEBUG
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=WARN

# ── Security (disabled — KrakenD handles auth at gateway) ─────────────────────
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration

# ── App custom config ─────────────────────────────────────────────────────────
mediq.kafka.topic.user-events=mediq.user.events
mediq.cache.user.ttl-minutes=30
mediq.keycloak.admin-url=${KEYCLOAK_ADMIN_URL:http://localhost:8090}
mediq.keycloak.realm=mediq
mediq.keycloak.admin-client-id=mediq-admin-cli
mediq.keycloak.admin-client-secret=${KEYCLOAK_ADMIN_SECRET:admin-secret}
```

---

### Step 3: Flyway Migration

> 🤔 **Before implementing — think about this:**
> Flyway migration files follow a naming convention: `V{version}__{description}.sql`. Why is the version number important? What happens if two developers create a migration with the same version number?

Create file: `src/main/resources/db/migration/V1__create_user_schema.sql`

Paste the full SQL schema from the **Database Schema** section above into this file.

---

### Step 4: Domain Model (JPA Entities)

> 🤔 **Before implementing — think about this:**
> The `user` table has a `keycloak_id` column that starts as NULL and gets filled later when Keycloak sync completes. What does this tell you about the registration flow? At what point is a user "complete" in the system?

Create the following entities in `src/main/java/com/mediq/model/`:

**`UserType.java`** — enum:
```java
package com.mediq.model;
public enum UserType { PATIENT, DOCTOR, ADMIN }
```

**`VerificationStatus.java`** — enum:
```java
package com.mediq.model;
public enum VerificationStatus { PENDING, VERIFIED, REJECTED }
```

**`AddressType.java`** — enum:
```java
package com.mediq.model;
public enum AddressType { HOME, WORK, BILLING }
```

**`ContactType.java`** — enum:
```java
package com.mediq.model;
public enum ContactType { EMAIL, PHONE }
```

**`UserEntity.java`:**
```java
package com.mediq.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "mediq_users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_id")
    private String keycloakId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserAddressEntity> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserContactEntity> contacts = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DoctorProfileEntity doctorProfile;

    @PreUpdate
    public void onUpdate() { this.updatedAt = Instant.now(); }

    // Getters and setters — generate all
}
```

**`UserAddressEntity.java`:**
```java
package com.mediq.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_address", schema = "mediq_users")
public class UserAddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false)
    private AddressType addressType;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String country = "India";

    @Column(nullable = false)
    private String zip;

    @Column(name = "is_primary")
    private boolean primary = false;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // Getters and setters — generate all
}
```

**`UserContactEntity.java`:**
```java
package com.mediq.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_contact", schema = "mediq_users")
public class UserContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false)
    private ContactType contactType;

    @Column(name = "contact_value", nullable = false)
    private String contactValue;

    @Column(name = "is_primary")
    private boolean primary = false;

    @Column(name = "is_verified")
    private boolean verified = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // Getters and setters — generate all
}
```

**`DoctorProfileEntity.java`:**
```java
package com.mediq.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "doctor_profile", schema = "mediq_users")
public class DoctorProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "license_number", nullable = false, unique = true)
    private String licenseNumber;

    @Column(name = "license_expiry", nullable = false)
    private LocalDate licenseExpiry;

    @Column(name = "years_of_experience")
    private int yearsOfExperience;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // Getters and setters — generate all
}
```

---

### Step 5: DTOs (Request / Response)

> 🤔 **Before implementing — think about this:**
> Why do we use separate DTO classes instead of directly exposing JPA entities in the API? What problems arise if you return a `UserEntity` directly from a controller?

Create in `src/main/java/com/mediq/dto/`:

**`RegisterPatientRequest.java`:**
```java
package com.mediq.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record RegisterPatientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    @NotEmpty @Valid List<ContactRequest> contacts,
    @Valid List<AddressRequest> addresses,
    @NotBlank String password
) {}
```

**`RegisterDoctorRequest.java`:**
```java
package com.mediq.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record RegisterDoctorRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    @NotEmpty @Valid List<ContactRequest> contacts,
    @Valid List<AddressRequest> addresses,
    @NotBlank String password,
    @NotBlank String licenseNumber,
    @NotNull LocalDate licenseExpiry,
    @Min(0) int yearsOfExperience
) {}
```

**`ContactRequest.java`:**
```java
package com.mediq.dto;

import com.mediq.model.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContactRequest(
    @NotNull ContactType contactType,
    @NotBlank String contactValue,
    boolean isPrimary
) {}
```

**`AddressRequest.java`:**
```java
package com.mediq.dto;

import com.mediq.model.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddressRequest(
    @NotNull AddressType addressType,
    @NotBlank String addressLine1,
    String addressLine2,
    @NotBlank String city,
    @NotBlank String state,
    @NotBlank String zip,
    boolean isPrimary
) {}
```

**`UserResponse.java`:**
```java
package com.mediq.dto;

import com.mediq.model.UserType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String keycloakId,
    UserType userType,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    boolean active,
    boolean verified,
    List<ContactResponse> contacts,
    List<AddressResponse> addresses,
    DoctorProfileResponse doctorProfile,
    Instant createdAt
) {}
```

**`ContactResponse.java`:**
```java
package com.mediq.dto;
import com.mediq.model.ContactType;
import java.util.UUID;
public record ContactResponse(UUID id, ContactType contactType,
    String contactValue, boolean isPrimary, boolean isVerified) {}
```

**`AddressResponse.java`:**
```java
package com.mediq.dto;
import com.mediq.model.AddressType;
import java.util.UUID;
public record AddressResponse(UUID id, AddressType addressType,
    String addressLine1, String addressLine2,
    String city, String state, String country, String zip, boolean isPrimary) {}
```

**`DoctorProfileResponse.java`:**
```java
package com.mediq.dto;
import com.mediq.model.VerificationStatus;
import java.time.LocalDate;
import java.util.UUID;
public record DoctorProfileResponse(UUID id, String licenseNumber,
    LocalDate licenseExpiry, int yearsOfExperience,
    VerificationStatus verificationStatus) {}
```

**`DoctorVerificationRequest.java`:**
```java
package com.mediq.dto;
import com.mediq.model.VerificationStatus;
import jakarta.validation.constraints.NotNull;
public record DoctorVerificationRequest(
    @NotNull VerificationStatus status,
    String rejectionReason
) {}
```

---

### Step 6: Kafka Event Classes

> 🤔 **Before implementing — think about this:**
> Every Kafka event has an `eventId` and `occurredAt` field in addition to the business data. Why? What problem does `eventId` solve that business IDs alone cannot?

Create in `src/main/java/com/mediq/event/`:

**`UserEvent.java`** — base event:
```java
package com.mediq.event;

import java.time.Instant;
import java.util.UUID;

public record UserEvent(
    String eventId,          // unique per event — idempotency key for consumers
    String eventType,        // USER_REGISTERED, USER_UPDATED, USER_DEACTIVATED, DOCTOR_VERIFIED
    String userId,
    String keycloakId,       // null until Keycloak sync completes
    String userType,         // PATIENT, DOCTOR, ADMIN
    String firstName,
    String lastName,
    String primaryEmail,
    String primaryPhone,
    String verificationStatus, // null for non-doctors
    Instant occurredAt
) {
    public static UserEvent of(String eventType, String userId,
            String keycloakId, String userType, String firstName,
            String lastName, String email, String phone,
            String verificationStatus) {
        return new UserEvent(
            UUID.randomUUID().toString(),
            eventType,
            userId,
            keycloakId,
            userType,
            firstName,
            lastName,
            email,
            phone,
            verificationStatus,
            Instant.now()
        );
    }
}
```

---

### Step 7: Repository Layer

Create in `src/main/java/com/mediq/repository/`:

**`UserRepository.java`:**
```java
package com.mediq.repository;

import com.mediq.model.UserEntity;
import com.mediq.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByKeycloakId(String keycloakId);

    List<UserEntity> findByUserTypeAndActive(UserType userType, boolean active);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.contacts LEFT JOIN FETCH u.addresses WHERE u.id = :id")
    Optional<UserEntity> findByIdWithDetails(UUID id);

    boolean existsByIdAndActive(UUID id, boolean active);
}
```

**`DoctorProfileRepository.java`:**
```java
package com.mediq.repository;

import com.mediq.model.DoctorProfileEntity;
import com.mediq.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfileEntity, UUID> {
    Optional<DoctorProfileEntity> findByUserId(UUID userId);
    List<DoctorProfileEntity> findByVerificationStatus(VerificationStatus status);
}
```

---

### Step 8: Kafka Producer

> 🤔 **Before implementing — think about this:**
> The Kafka producer is called AFTER the DB save succeeds. What happens if the DB save succeeds but the Kafka publish fails? The user is created but no downstream service knows about it. How would you solve this in production?
> *(This is the Outbox Pattern problem — note your answer, we will implement Outbox in a later task)*

Create `src/main/java/com/mediq/event/UserEventPublisher.java`:

```java
package com.mediq.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private final String topic;

    public UserEventPublisher(
            KafkaTemplate<String, UserEvent> kafkaTemplate,
            @Value("${mediq.kafka.topic.user-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(UserEvent event) {
        // userId as partition key — ensures all events for same user
        // go to same partition — ordering guaranteed per user
        CompletableFuture<SendResult<String, UserEvent>> future =
            kafkaTemplate.send(topic, event.userId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event={} for userId={}: {}",
                    event.eventType(), event.userId(), ex.getMessage());
                // TODO: Implement Outbox pattern here in M2.x task
                // For now: log and continue — eventual consistency
            } else {
                log.info("Published event={} for userId={} to partition={}",
                    event.eventType(), event.userId(),
                    result.getRecordMetadata().partition());
            }
        });
    }
}
```

---

### Step 9: Service Layer

> 🤔 **Before implementing — think about this:**
> `registerPatient` and `registerDoctor` both save a user. Should they be in one method or two? What happens to the Open/Closed Principle if you add a third user type (e.g., CLINIC_ADMIN) later?

Create `src/main/java/com/mediq/service/UserService.java`:

```java
package com.mediq.service;

import com.mediq.dto.*;
import com.mediq.event.UserEvent;
import com.mediq.event.UserEventPublisher;
import com.mediq.exception.UserNotFoundException;
import com.mediq.model.*;
import com.mediq.repository.DoctorProfileRepository;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final UserEventPublisher eventPublisher;
    private final UserCacheService cacheService;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       DoctorProfileRepository doctorProfileRepository,
                       UserEventPublisher eventPublisher,
                       UserCacheService cacheService,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse registerPatient(RegisterPatientRequest request) {
        log.info("Registering patient: {}", request.firstName());

        UserEntity user = userMapper.toEntity(request, UserType.PATIENT);
        userRepository.save(user);

        // Publish event — downstream services (including Keycloak sync) react
        UserEvent event = buildEvent("USER_REGISTERED", user, request.contacts());
        eventPublisher.publish(event);

        log.info("Patient registered: userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse registerDoctor(RegisterDoctorRequest request) {
        log.info("Registering doctor: {}", request.firstName());

        UserEntity user = userMapper.toEntity(request, UserType.DOCTOR);
        userRepository.save(user);

        // Create doctor profile
        DoctorProfileEntity profile = new DoctorProfileEntity();
        profile.setUser(user);
        profile.setLicenseNumber(request.licenseNumber());
        profile.setLicenseExpiry(request.licenseExpiry());
        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setVerificationStatus(VerificationStatus.PENDING);
        doctorProfileRepository.save(profile);

        UserEvent event = buildEvent("USER_REGISTERED", user, request.contacts());
        eventPublisher.publish(event);

        log.info("Doctor registered (PENDING verification): userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        // Try cache first
        UserResponse cached = cacheService.get(userId);
        if (cached != null) return cached;

        UserEntity user = userRepository.findByIdWithDetails(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        UserResponse response = userMapper.toResponse(user);
        cacheService.put(userId, response);
        return response;
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId, UUID requestedBy) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setActive(false);
        user.setUpdatedBy(requestedBy);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        cacheService.evict(userId);

        UserEvent event = buildEvent("USER_DEACTIVATED", user,
            user.getContacts().stream()
                .map(c -> new ContactRequest(c.getContactType(), c.getContactValue(), c.isPrimary()))
                .toList());
        eventPublisher.publish(event);

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse verifyDoctor(UUID doctorUserId, DoctorVerificationRequest request, UUID adminId) {
        UserEntity user = userRepository.findByIdWithDetails(doctorUserId)
            .orElseThrow(() -> new UserNotFoundException(doctorUserId));

        DoctorProfileEntity profile = doctorProfileRepository.findByUserId(doctorUserId)
            .orElseThrow(() -> new IllegalStateException("Doctor profile not found for userId: " + doctorUserId));

        profile.setVerificationStatus(request.status());
        profile.setVerifiedBy(adminId);
        profile.setVerifiedAt(Instant.now());

        if (request.status() == VerificationStatus.REJECTED) {
            profile.setRejectionReason(request.rejectionReason());
        }

        if (request.status() == VerificationStatus.VERIFIED) {
            user.setVerified(true);
        }

        doctorProfileRepository.save(profile);
        userRepository.save(user);
        cacheService.evict(doctorUserId);

        UserEvent event = buildEvent("DOCTOR_VERIFIED", user,
            user.getContacts().stream()
                .map(c -> new ContactRequest(c.getContactType(), c.getContactValue(), c.isPrimary()))
                .toList());
        eventPublisher.publish(event);

        return userMapper.toResponse(user);
    }

    public List<UserResponse> getPendingDoctorVerifications() {
        return doctorProfileRepository
            .findByVerificationStatus(VerificationStatus.PENDING)
            .stream()
            .map(p -> userMapper.toResponse(p.getUser()))
            .toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private UserEvent buildEvent(String eventType, UserEntity user,
                                 List<ContactRequest> contacts) {
        String email = contacts.stream()
            .filter(c -> c.contactType() == ContactType.EMAIL && c.isPrimary())
            .map(ContactRequest::contactValue)
            .findFirst().orElse(null);

        String phone = contacts.stream()
            .filter(c -> c.contactType() == ContactType.PHONE && c.isPrimary())
            .map(ContactRequest::contactValue)
            .findFirst().orElse(null);

        String verificationStatus = user.getDoctorProfile() != null
            ? user.getDoctorProfile().getVerificationStatus().name()
            : null;

        return UserEvent.of(eventType,
            user.getId().toString(),
            user.getKeycloakId(),
            user.getUserType().name(),
            user.getFirstName(),
            user.getLastName(),
            email, phone, verificationStatus);
    }
}
```

---

### Step 10: Cache Service

Create `src/main/java/com/mediq/service/UserCacheService.java`:

```java
package com.mediq.service;

import com.mediq.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class UserCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserCacheService.class);
    private static final String KEY_PREFIX = "user:";

    private final RedisTemplate<String, UserResponse> redisTemplate;
    private final Duration ttl;

    public UserCacheService(
            RedisTemplate<String, UserResponse> redisTemplate,
            @Value("${mediq.cache.user.ttl-minutes}") int ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public UserResponse get(UUID userId) {
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis read failed for userId={}: {}", userId, e.getMessage());
            return null; // fail open — fallback to DB
        }
    }

    public void put(UUID userId, UserResponse response) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, response, ttl);
        } catch (Exception e) {
            log.warn("Redis write failed for userId={}: {}", userId, e.getMessage());
            // fail open — cache miss on next read is acceptable
        }
    }

    public void evict(UUID userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis evict failed for userId={}: {}", userId, e.getMessage());
        }
    }
}
```

---

### Step 11: UserMapper

> 🤔 **Before implementing — think about this:**
> `UserMapper` converts between Entity and DTO. Why is this conversion important? What is the risk of using the same object for both database persistence and API response?

Create `src/main/java/com/mediq/service/UserMapper.java`:

```java
package com.mediq.service;

import com.mediq.dto.*;
import com.mediq.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserEntity toEntity(RegisterPatientRequest request, UserType type) {
        UserEntity user = new UserEntity();
        user.setUserType(type);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setDateOfBirth(request.dateOfBirth());

        List<UserContactEntity> contacts = request.contacts().stream()
            .map(c -> toContactEntity(c, user))
            .toList();
        user.getContacts().addAll(contacts);

        if (request.addresses() != null) {
            List<UserAddressEntity> addresses = request.addresses().stream()
                .map(a -> toAddressEntity(a, user))
                .toList();
            user.getAddresses().addAll(addresses);
        }

        return user;
    }

    public UserEntity toEntity(RegisterDoctorRequest request, UserType type) {
        UserEntity user = new UserEntity();
        user.setUserType(type);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setDateOfBirth(request.dateOfBirth());

        List<UserContactEntity> contacts = request.contacts().stream()
            .map(c -> toContactEntity(c, user))
            .toList();
        user.getContacts().addAll(contacts);

        if (request.addresses() != null) {
            List<UserAddressEntity> addresses = request.addresses().stream()
                .map(a -> toAddressEntity(a, user))
                .toList();
            user.getAddresses().addAll(addresses);
        }

        return user;
    }

    public UserResponse toResponse(UserEntity user) {
        DoctorProfileResponse doctorProfile = null;
        if (user.getDoctorProfile() != null) {
            DoctorProfileEntity dp = user.getDoctorProfile();
            doctorProfile = new DoctorProfileResponse(
                dp.getId(), dp.getLicenseNumber(),
                dp.getLicenseExpiry(), dp.getYearsOfExperience(),
                dp.getVerificationStatus());
        }

        return new UserResponse(
            user.getId(),
            user.getKeycloakId(),
            user.getUserType(),
            user.getFirstName(),
            user.getLastName(),
            user.getDateOfBirth(),
            user.isActive(),
            user.isVerified(),
            user.getContacts().stream().map(this::toContactResponse).toList(),
            user.getAddresses().stream().map(this::toAddressResponse).toList(),
            doctorProfile,
            user.getCreatedAt()
        );
    }

    private UserContactEntity toContactEntity(ContactRequest c, UserEntity user) {
        UserContactEntity entity = new UserContactEntity();
        entity.setUser(user);
        entity.setContactType(c.contactType());
        entity.setContactValue(c.contactValue());
        entity.setPrimary(c.isPrimary());
        return entity;
    }

    private UserAddressEntity toAddressEntity(AddressRequest a, UserEntity user) {
        UserAddressEntity entity = new UserAddressEntity();
        entity.setUser(user);
        entity.setAddressType(a.addressType());
        entity.setAddressLine1(a.addressLine1());
        entity.setAddressLine2(a.addressLine2());
        entity.setCity(a.city());
        entity.setState(a.state());
        entity.setZip(a.zip());
        entity.setPrimary(a.isPrimary());
        return entity;
    }

    private ContactResponse toContactResponse(UserContactEntity c) {
        return new ContactResponse(c.getId(), c.getContactType(),
            c.getContactValue(), c.isPrimary(), c.isVerified());
    }

    private AddressResponse toAddressResponse(UserAddressEntity a) {
        return new AddressResponse(a.getId(), a.getAddressType(),
            a.getAddressLine1(), a.getAddressLine2(),
            a.getCity(), a.getState(), a.getCountry(), a.getZip(), a.isPrimary());
    }
}
```

---

### Step 12: Exception Classes

Create in `src/main/java/com/mediq/exception/`:

**`UserNotFoundException.java`:**
```java
package com.mediq.exception;
import java.util.UUID;
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
```

**`ErrorResponse.java`:**
```java
package com.mediq.exception;
import java.time.Instant;
public record ErrorResponse(String error, String message,
    String path, Instant timestamp) {}
```

**`GlobalExceptionHandler.java`:**
```java
package com.mediq.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            UserNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage(),
                req.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", message,
                req.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", ex.getMessage(),
                req.getRequestURI(), Instant.now()));
    }
}
```

---

### Step 13: REST Controller

> 🤔 **Before implementing — think about this:**
> The controller reads `X-User-Id` and `X-User-Role` from request headers. These come from KrakenD's JWT claim propagation. What security assumption does this create? What would happen if a malicious actor called user-service directly (bypassing KrakenD) with forged headers?

Create `src/main/java/com/mediq/controller/UserController.java`:

```java
package com.mediq.controller;

import com.mediq.dto.*;
import com.mediq.interceptor.UserContextHolder;
import com.mediq.model.UserContext;
import com.mediq.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── Public endpoints (no auth required in KrakenD) ────────────────────────

    @PostMapping("/patients/register")
    public ResponseEntity<UserResponse> registerPatient(
            @Valid @RequestBody RegisterPatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.registerPatient(request));
    }

    @PostMapping("/doctors/register")
    public ResponseEntity<UserResponse> registerDoctor(
            @Valid @RequestBody RegisterDoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.registerDoctor(request));
    }

    // ── Protected endpoints (JWT required via KrakenD) ────────────────────────

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        UserContext ctx = UserContextHolder.get();
        log.info("GET /users/{} by userId={} role={}", userId, ctx.userId(), ctx.role());
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID userId) {
        UserContext ctx = UserContextHolder.get();
        UUID requestedBy = UUID.fromString(ctx.userId());
        return ResponseEntity.ok(userService.deactivateUser(userId, requestedBy));
    }

    // ── Admin-only endpoints ──────────────────────────────────────────────────

    @GetMapping("/doctors/pending-verification")
    public ResponseEntity<List<UserResponse>> getPendingVerifications() {
        UserContext ctx = UserContextHolder.get();
        if (!"ADMIN".equals(ctx.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.getPendingDoctorVerifications());
    }

    @PutMapping("/doctors/{doctorUserId}/verify")
    public ResponseEntity<UserResponse> verifyDoctor(
            @PathVariable UUID doctorUserId,
            @Valid @RequestBody DoctorVerificationRequest request) {
        UserContext ctx = UserContextHolder.get();
        if (!"ADMIN".equals(ctx.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UUID adminId = UUID.fromString(ctx.userId());
        return ResponseEntity.ok(userService.verifyDoctor(doctorUserId, request, adminId));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("user-service UP");
    }
}
```

---

### Step 14: Keep Existing Interceptor Pattern

Keep `JwtClaimsInterceptor`, `UserContextHolder`, `UserContext`, `WebMvcConfig` exactly as they are — just change the package from `com.trucare` to `com.mediq`.

---

### Step 15: Keycloak Sync Consumer

> 🤔 **Before implementing — think about this:**
> This consumer reads `USER_REGISTERED` events from Kafka and creates the identity in Keycloak. What happens if Keycloak is temporarily down? The event will fail. How does Kafka's at-least-once guarantee help here? What does idempotency mean in this context?

Create `src/main/java/com/mediq/keycloak/KeycloakSyncConsumer.java`:

```java
package com.mediq.keycloak;

import com.mediq.event.UserEvent;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class KeycloakSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(KeycloakSyncConsumer.class);

    private final KeycloakAdminClient keycloakAdminClient;
    private final UserRepository userRepository;

    public KeycloakSyncConsumer(KeycloakAdminClient keycloakAdminClient,
                                UserRepository userRepository) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.userRepository = userRepository;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.user-events}",
        groupId = "mediq-keycloak-sync-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserEvent(UserEvent event, Acknowledgment ack) {
        log.info("Keycloak sync received eventType={} userId={}",
            event.eventType(), event.userId());

        try {
            switch (event.eventType()) {
                case "USER_REGISTERED" -> handleUserRegistered(event);
                case "USER_DEACTIVATED" -> handleUserDeactivated(event);
                default -> log.debug("Ignoring event type: {}", event.eventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Keycloak sync failed for userId={}: {}",
                event.userId(), e.getMessage());
            // Do NOT acknowledge — Kafka retries
            // Keycloak sync is idempotent: creating same user twice
            // returns existing user (no duplicate created)
        }
    }

    private void handleUserRegistered(UserEvent event) {
        // Idempotency check — if keycloakId already set, skip
        userRepository.findById(UUID.fromString(event.userId()))
            .filter(u -> u.getKeycloakId() != null)
            .ifPresent(u -> {
                throw new IllegalStateException(
                    "Keycloak ID already set for userId=" + event.userId() + " — skipping");
            });

        String keycloakId = keycloakAdminClient.createUser(
            event.primaryEmail(),
            event.firstName() + " " + event.lastName(),
            event.userType()
        );

        // Update user record with keycloak_id
        userRepository.findById(UUID.fromString(event.userId()))
            .ifPresent(user -> {
                user.setKeycloakId(keycloakId);
                userRepository.save(user);
                log.info("Keycloak ID set for userId={}: keycloakId={}",
                    event.userId(), keycloakId);
            });
    }

    private void handleUserDeactivated(UserEvent event) {
        if (event.keycloakId() != null) {
            keycloakAdminClient.disableUser(event.keycloakId());
            log.info("Keycloak user disabled: keycloakId={}", event.keycloakId());
        }
    }
}
```

Create `src/main/java/com/mediq/keycloak/KeycloakAdminClient.java`:

```java
package com.mediq.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final RestTemplate restTemplate;
    private final String adminUrl;
    private final String realm;

    public KeycloakAdminClient(
            @Value("${mediq.keycloak.admin-url}") String adminUrl,
            @Value("${mediq.keycloak.realm}") String realm) {
        this.restTemplate = new RestTemplate();
        this.adminUrl = adminUrl;
        this.realm = realm;
    }

    public String createUser(String email, String fullName, String role) {
        String token = getAdminToken();
        String url = adminUrl + "/admin/realms/" + realm + "/users";

        Map<String, Object> body = Map.of(
            "username", email,
            "email", email,
            "firstName", fullName.split(" ")[0],
            "lastName", fullName.contains(" ") ? fullName.split(" ")[1] : "",
            "enabled", true,
            "emailVerified", false,
            "realmRoles", List.of(role)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);

        // Keycloak returns Location header with new user URL
        String location = response.getHeaders().getFirst("Location");
        if (location == null) throw new RuntimeException("No Location header from Keycloak");

        String keycloakId = location.substring(location.lastIndexOf('/') + 1);
        log.info("Created Keycloak user: keycloakId={}", keycloakId);
        return keycloakId;
    }

    public void disableUser(String keycloakId) {
        String token = getAdminToken();
        String url = adminUrl + "/admin/realms/" + realm + "/users/" + keycloakId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("enabled", false);
        restTemplate.exchange(url, HttpMethod.PUT,
            new HttpEntity<>(body, headers), Void.class);
    }

    private String getAdminToken() {
        // TODO: Implement proper client credentials flow
        // For now: use Keycloak admin credentials directly
        // In production: use client_credentials grant with mediq-admin-cli client
        return "TODO-implement-admin-token-fetch";
    }
}
```

---

### Step 16: Spring Configuration

Create `src/main/java/com/mediq/config/KafkaConfig.java`:

```java
package com.mediq.config;

import com.mediq.event.UserEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, UserEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "mediq-keycloak-sync-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        JsonDeserializer<UserEvent> deserializer = new JsonDeserializer<>(UserEvent.class);
        deserializer.addTrustedPackages("com.mediq.event");

        return new DefaultKafkaConsumerFactory<>(props,
            new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserEvent>
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(
            ContainerProperties.AckMode.MANUAL); // manual commit
        return factory;
    }
}
```

Create `src/main/java/com/mediq/config/RedisConfig.java`:

```java
package com.mediq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediq.dto.UserResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, UserResponse> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, UserResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<UserResponse> serializer =
            new Jackson2JsonRedisSerializer<>(mapper, UserResponse.class);

        template.setValueSerializer(serializer);
        return template;
    }
}
```

---

### Step 17: Update docker-compose.yml

> 🤔 **Before implementing — think about this:**
> You are adding PostgreSQL, Kafka, Zookeeper, and Redis to docker-compose. The existing services (KrakenD, Keycloak) have `depends_on` with `condition: service_healthy`. Why is `service_healthy` important here rather than just `service_started`?

Add to `docker-compose.yml` — replace the entire file:

```yaml
version: "3.9"

networks:
  mediq-net:
    driver: bridge

volumes:
  postgres-data:
  redis-data:
  kafka-data:

services:

  # ── PostgreSQL ───────────────────────────────────────────────────────────────
  postgres:
    image: postgres:16-alpine
    container_name: mediq-postgres
    environment:
      POSTGRES_USER: mediq
      POSTGRES_PASSWORD: mediq
      POSTGRES_DB: mediq_users
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - mediq-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U mediq -d mediq_users"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Redis ────────────────────────────────────────────────────────────────────
  redis:
    image: redis:7-alpine
    container_name: mediq-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - mediq-net
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  # ── Zookeeper (required by Kafka) ────────────────────────────────────────────
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: mediq-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - mediq-net
    healthcheck:
      test: ["CMD", "bash", "-c", "echo ruok | nc localhost 2181 | grep imok"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Kafka ────────────────────────────────────────────────────────────────────
  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: mediq-kafka
    depends_on:
      zookeeper:
        condition: service_healthy
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_LOG_RETENTION_HOURS: 168
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks:
      - mediq-net
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s

  # ── Keycloak ─────────────────────────────────────────────────────────────────
  keycloak:
    image: quay.io/keycloak/keycloak:24.0.3
    container_name: mediq-keycloak
    command:
      - start-dev
      - --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_HTTP_PORT: 8090
      KC_HOSTNAME_STRICT: "false"
      KC_HOSTNAME_STRICT_HTTPS: "false"
      KC_HTTP_ENABLED: "true"
    ports:
      - "8090:8090"
    volumes:
      - ./keycloak/realm:/opt/keycloak/data/import
    networks:
      - mediq-net
    healthcheck:
      test: ["CMD", "/bin/bash", "-c",
             "exec 3<>/dev/tcp/localhost/8090 && printf 'GET /realms/mediq HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && timeout 3 cat <&3 | head -1 | grep -q '200'"]
      interval: 30s
      timeout: 10s
      retries: 10
      start_period: 90s

  # ── user-service ─────────────────────────────────────────────────────────────
  user-service:
    build:
      context: ./user-service
      dockerfile: Dockerfile
    container_name: mediq-user-service
    ports:
      - "8081:8081"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/mediq_users
      DB_USERNAME: mediq
      DB_PASSWORD: mediq
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      REDIS_HOST: redis
      REDIS_PORT: 6379
      KEYCLOAK_ADMIN_URL: http://keycloak:8090
      KEYCLOAK_ADMIN_SECRET: admin
    networks:
      - mediq-net
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
      keycloak:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
             "http://localhost:8081/actuator/health"]
      interval: 20s
      timeout: 5s
      retries: 5
      start_period: 45s

  # ── KrakenD ──────────────────────────────────────────────────────────────────
  krakend:
    image: devopsfaith/krakend:2.7
    container_name: mediq-krakend
    ports:
      - "8080:8080"
    volumes:
      - ./krakend:/etc/krakend
    environment:
      FC_ENABLE: "1"
      FC_TEMPLATES: "/etc/krakend/partials"
      FC_SETTINGS: "/etc/krakend/settings"
    command: ["run", "--config", "/etc/krakend/krakend.tmpl", "--debug"]
    networks:
      - mediq-net
    depends_on:
      keycloak:
        condition: service_healthy
      user-service:
        condition: service_healthy
```

---

### Step 18: Update Keycloak Realm

Update `keycloak/realm/trucare-realm.json`:
- Change `"realm": "trucare"` → `"realm": "mediq"`
- Change all references from `trucare` → `mediq`
- Keep existing roles: `PATIENT`, `DOCTOR`, `NURSE`, `ADMIN`

Rename file to: `keycloak/realm/mediq-realm.json`

---

### Step 19: Update KrakenD Config

In `krakend/settings/hosts.json` — update service host:
```json
{
  "user_service": ["http://user-service:8081"]
}
```

In `krakend/krakend.json` — update endpoints to point to `user-service`:
- Change `"host": ["http://patient-service:8081"]` → `"http://user-service:8081"`
- Update `jwk_url` realm from `trucare` → `mediq`
- Add new endpoints for user-service:

```json
{
  "endpoint": "/api/v1/users/patients/register",
  "method": "POST",
  "backend": [{
    "url_pattern": "/users/patients/register",
    "host": ["http://user-service:8081"]
  }]
},
{
  "endpoint": "/api/v1/users/doctors/register",
  "method": "POST",
  "backend": [{
    "url_pattern": "/users/doctors/register",
    "host": ["http://user-service:8081"]
  }]
},
{
  "endpoint": "/api/v1/users/{userId}",
  "method": "GET",
  "extra_config": {
    "auth/validator": {
      "alg": "RS256",
      "jwk_url": "http://keycloak:8090/realms/mediq/protocol/openid-connect/certs",
      "issuer": "http://localhost:8090/realms/mediq",
      "cache": true,
      "roles_key": "role",
      "roles": ["PATIENT", "DOCTOR", "ADMIN"],
      "propagate_claims": [
        ["sub", "X-User-Id"],
        ["email", "X-User-Email"],
        ["role", "X-User-Role"],
        ["name", "X-User-Name"]
      ]
    }
  },
  "backend": [{
    "url_pattern": "/users/{userId}",
    "host": ["http://user-service:8081"]
  }]
}
```

---

### Step 20: Main Application Class

Update `UserServiceApplication.java`:

```java
package com.mediq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

---

## Verification Steps

After implementation, verify each of these:

### 1. Build succeeds
```powershell
cd D:\codebase\krakend_explore\user-service
mvn clean package -DskipTests
```

### 2. Docker Compose starts all services
```powershell
cd D:\codebase\krakend_explore
docker compose up --build
```

Expected healthy services:
```
mediq-postgres     ✅
mediq-redis        ✅
mediq-zookeeper    ✅
mediq-kafka        ✅
mediq-keycloak     ✅
mediq-user-service ✅
mediq-krakend      ✅
```

### 3. Register a patient
```powershell
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Rahul",
    "lastName": "Sharma",
    "dateOfBirth": "1990-05-15",
    "password": "Test@1234",
    "contacts": [
      {"contactType": "EMAIL", "contactValue": "rahul@example.com", "isPrimary": true},
      {"contactType": "PHONE", "contactValue": "9876543210", "isPrimary": true}
    ],
    "addresses": [
      {
        "addressType": "HOME",
        "addressLine1": "123 MG Road",
        "city": "Bengaluru",
        "state": "Karnataka",
        "zip": "560001",
        "isPrimary": true
      }
    ]
  }'
```

Expected: `201 Created` with `UserResponse` JSON

### 4. Register a doctor
```powershell
curl -X POST http://localhost:8080/api/v1/users/doctors/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Priya",
    "lastName": "Verma",
    "dateOfBirth": "1985-08-20",
    "password": "Test@1234",
    "licenseNumber": "MCI-2024-98765",
    "licenseExpiry": "2027-12-31",
    "yearsOfExperience": 10,
    "contacts": [
      {"contactType": "EMAIL", "contactValue": "dr.priya@mediq.com", "isPrimary": true}
    ]
  }'
```

Expected: `201 Created`, `verificationStatus: PENDING`

### 5. Verify Kafka event published
```powershell
# From WSL
docker exec -it mediq-kafka kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic mediq.user.events `
  --from-beginning `
  --max-messages 5
```

Expected: JSON UserEvent with `eventType: USER_REGISTERED`

### 6. Verify Redis cache
```powershell
docker exec -it mediq-redis redis-cli
> KEYS user:*
> GET user:{userId-from-register-response}
```

### 7. Health check
```powershell
curl http://localhost:8081/actuator/health
```

Expected: DB, Redis, Kafka all showing UP

---

## Known TODOs for Future Tasks

```
1. Outbox Pattern (Task M2.x)
   Currently: Kafka publish can fail after DB save
   Fix: Write to outbox table in same transaction,
        Debezium CDC publishes to Kafka

2. Keycloak Admin Token (KeycloakAdminClient.java)
   Currently: placeholder getAdminToken()
   Fix: Implement client_credentials grant flow

3. Doctor verification email notification
   Currently: event published, no notification sent
   Fix: notification-service consumes DOCTOR_VERIFIED event

4. Input validation
   Currently: basic @NotBlank annotations
   Fix: email format validation, phone format validation,
        duplicate email check

5. Liveness + Readiness probes (Task M1.3)
   Currently: basic actuator health
   Fix: custom HealthIndicator per dependency
```

---

## What to Bring Back for Review

When done, zip the `user-service/` folder and share here. We will review:
1. Did Flyway migrations run cleanly?
2. Is the Kafka event payload correct?
3. Is Redis cache being populated on GET?
4. Did the Keycloak sync consumer receive the event?
5. Any compilation errors or design decisions Claude Code made differently than expected?
