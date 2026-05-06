# mediq — Task M5b: Payment Service with Stripe

## Branch
```powershell
git checkout main
git pull origin main
git checkout -b feature/mediq-m5b-payment-stripe
```

## Prerequisites
```
✅ Stripe CLI installed (stripe version 1.40.9)
✅ Stripe CLI logged in (acct_1TTkyRAUpS2XYrXe)
✅ Temporal running (from M5a)

Before starting:
1. Get Stripe test secret key:
   https://dashboard.stripe.com/test/apikeys
   Copy: sk_test_... (Secret key)

2. Get Stripe webhook signing secret:
   We will get this from stripe listen command output
```

## What This Builds
```
payment-service (port 8089):
  POST /payments/intent        ← creates Stripe PaymentIntent
  POST /webhooks/stripe        ← receives Stripe webhook events
  GET  /payments/{paymentId}   ← get payment status

Flow:
  Temporal workflow → PaymentActivities.createPaymentIntent()
  → payment-service calls Stripe API → PaymentIntent created
  → returns clientSecret to Temporal
  → Temporal waits for paymentCompleted signal (10min timeout)

  Stripe CLI forwards webhook → payment-service /webhooks/stripe
  payment_intent.succeeded → payment-service sends Temporal signal
  payment_intent.payment_failed → payment-service sends Temporal signal
  → Temporal workflow resumes → confirms or cancels booking
```

## New Service: payment-service

### Create folder structure
```powershell
# From D:\codebase\krakend_explore
mkdir payment-service\src\main\java\com\mediq\payment\controller
mkdir payment-service\src\main\java\com\mediq\payment\service
mkdir payment-service\src\main\java\com\mediq\payment\model
mkdir payment-service\src\main\java\com\mediq\payment\event
mkdir payment-service\src\main\java\com\mediq\payment\config
mkdir payment-service\src\main\java\com\mediq\payment\exception
mkdir payment-service\src\main\resources\db\migration
```

### Step 1: pom.xml for payment-service

Create `payment-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
    </parent>

    <groupId>com.mediq</groupId>
    <artifactId>payment-service</artifactId>
    <version>1.0.0</version>
    <description>mediq Payment Service — Stripe Integration</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <!-- Stripe Java SDK -->
        <dependency>
            <groupId>com.stripe</groupId>
            <artifactId>stripe-java</artifactId>
            <version>25.6.0</version>
        </dependency>
        <!-- Temporal SDK — to send signals back to workflow -->
        <dependency>
            <groupId>io.temporal</groupId>
            <artifactId>temporal-sdk</artifactId>
            <version>1.24.1</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
        <finalName>payment-service-1.0.0</finalName>
    </build>
</project>
```

### Step 2: application.properties

Create `payment-service/src/main/resources/application.properties`:

```properties
# ── Server ────────────────────────────────────────────────────────────────────
server.port=8089
spring.application.name=payment-service

# ── Database ──────────────────────────────────────────────────────────────────
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediq_payments}
spring.datasource.username=${DB_USERNAME:mediq}
spring.datasource.password=${DB_PASSWORD:mediq}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.open-in-view=false

# ── Flyway ────────────────────────────────────────────────────────────────────
spring.flyway.enabled=true
spring.flyway.schemas=mediq_payments
spring.flyway.baseline-on-migrate=true

# ── Kafka ─────────────────────────────────────────────────────────────────────
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.producer.acks=all
spring.kafka.producer.properties.enable.idempotence=true

# ── Stripe ────────────────────────────────────────────────────────────────────
stripe.secret-key=${STRIPE_SECRET_KEY:sk_test_your_key_here}
stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_your_secret_here}
stripe.currency=inr

# ── Temporal ──────────────────────────────────────────────────────────────────
temporal.host=${TEMPORAL_HOST:localhost}
temporal.port=${TEMPORAL_PORT:7233}
temporal.namespace=mediq-appointments
temporal.task-queue=appointment-booking-queue

# ── Actuator ──────────────────────────────────────────────────────────────────
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always

# ── Kafka Topics ──────────────────────────────────────────────────────────────
mediq.kafka.topic.payment-events=mediq.payment.events

# ── Logging ───────────────────────────────────────────────────────────────────
logging.level.com.mediq=DEBUG
logging.level.com.stripe=INFO

# ── Security ──────────────────────────────────────────────────────────────────
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### Step 3: Flyway Migration

Create `payment-service/src/main/resources/db/migration/V1__create_payment_schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_payments;

CREATE TABLE payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID NOT NULL,
    patient_id          UUID NOT NULL,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    stripe_client_secret     VARCHAR(500),
    amount              DECIMAL(10,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'inr',
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN (
                            'PENDING',
                            'PROCESSING',
                            'SUCCEEDED',
                            'FAILED',
                            'CANCELLED',
                            'REFUNDED'
                        )),
    failure_reason      TEXT,
    temporal_workflow_id VARCHAR(255),    -- to send signal back
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_appointment ON payment(appointment_id);
CREATE INDEX idx_payment_stripe_intent ON payment(stripe_payment_intent_id);
CREATE INDEX idx_payment_status ON payment(status);
```

Also add to `scripts/postgres-init.sql`:
```sql
CREATE DATABASE mediq_payments;
GRANT ALL PRIVILEGES ON DATABASE mediq_payments TO mediq;
```

### Step 4: Payment Entity

Create `payment-service/src/main/java/com/mediq/payment/model/PaymentEntity.java`:

```java
package com.mediq.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment", schema = "mediq_payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "stripe_client_secret")
    private String stripeClientSecret;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "inr";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "temporal_workflow_id")
    private String temporalWorkflowId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() { this.updatedAt = Instant.now(); }

    // Getters and setters — generate all
}
```

Create `PaymentStatus.java`:
```java
package com.mediq.payment.model;
public enum PaymentStatus {
    PENDING, PROCESSING, SUCCEEDED, FAILED, CANCELLED, REFUNDED
}
```

### Step 5: Stripe Configuration Bean

Create `payment-service/src/main/java/com/mediq/payment/config/StripeConfig.java`:

```java
package com.mediq.payment.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        // Set Stripe API key globally
        Stripe.apiKey = secretKey;
    }
}
```

Create `TemporalConfig.java` in payment-service (same as appointment-service config):
```java
package com.mediq.payment.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    @Value("${temporal.host}:${temporal.port}")
    private String temporalTarget;

    @Value("${temporal.namespace}")
    private String namespace;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalTarget)
                .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build());
    }
}
```

### Step 6: Payment Service

Create `payment-service/src/main/java/com/mediq/payment/service/PaymentService.java`:

```java
package com.mediq.payment.service;

import com.mediq.payment.config.AppointmentBookingWorkflowSignal;
import com.mediq.payment.model.*;
import com.mediq.payment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final WorkflowClient workflowClient;
    private final String currency;

    public PaymentService(
            PaymentRepository paymentRepository,
            WorkflowClient workflowClient,
            @Value("${stripe.currency}") String currency) {
        this.paymentRepository = paymentRepository;
        this.workflowClient = workflowClient;
        this.currency = currency;
    }

    @Transactional
    public CreatePaymentIntentResponse createPaymentIntent(
            String appointmentId,
            String patientId,
            BigDecimal amount,
            String temporalWorkflowId) throws StripeException {

        // Amount in paise (Stripe uses smallest currency unit)
        // INR 500 = 50000 paise
        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountInPaise)
            .setCurrency(currency)
            .putMetadata("appointmentId", appointmentId)
            .putMetadata("patientId", patientId)
            .putMetadata("temporalWorkflowId", temporalWorkflowId)
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .build())
            .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        log.info("Stripe PaymentIntent created: {} for appointmentId: {}",
            paymentIntent.getId(), appointmentId);

        // Save to DB
        PaymentEntity payment = new PaymentEntity();
        payment.setAppointmentId(UUID.fromString(appointmentId));
        payment.setPatientId(UUID.fromString(patientId));
        payment.setStripePaymentIntentId(paymentIntent.getId());
        payment.setStripeClientSecret(paymentIntent.getClientSecret());
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTemporalWorkflowId(temporalWorkflowId);
        paymentRepository.save(payment);

        return new CreatePaymentIntentResponse(
            payment.getId().toString(),
            paymentIntent.getClientSecret(),
            paymentIntent.getId()
        );
    }

    @Transactional
    public void handleStripeWebhook(String paymentIntentId, boolean success,
                                     String failureReason) {
        paymentRepository.findByStripePaymentIntentId(paymentIntentId)
            .ifPresent(payment -> {
                // Update payment status
                payment.setStatus(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
                if (!success) payment.setFailureReason(failureReason);
                paymentRepository.save(payment);

                log.info("Payment {} for appointmentId={} success={}",
                    paymentIntentId, payment.getAppointmentId(), success);

                // Send signal to Temporal workflow to unblock it
                sendTemporalSignal(
                    payment.getTemporalWorkflowId(),
                    paymentIntentId,
                    success);
            });
    }

    private void sendTemporalSignal(String workflowId,
                                     String paymentIntentId,
                                     boolean success) {
        try {
            // Get stub to the RUNNING workflow in appointment-service
            AppointmentBookingWorkflowSignal workflow =
                workflowClient.newWorkflowStub(
                    AppointmentBookingWorkflowSignal.class, workflowId);

            // Send signal — Temporal routes this to the waiting workflow
            workflow.paymentCompleted(paymentIntentId, success);

            log.info("Temporal signal sent to workflowId={}: success={}",
                workflowId, success);
        } catch (Exception e) {
            log.error("Failed to send Temporal signal to workflowId={}: {}",
                workflowId, e.getMessage());
        }
    }
}
```

Create `CreatePaymentIntentResponse.java`:
```java
package com.mediq.payment.service;
public record CreatePaymentIntentResponse(
    String paymentId,
    String clientSecret,
    String paymentIntentId
) {}
```

Create `AppointmentBookingWorkflowSignal.java` — interface to send signal:
```java
package com.mediq.payment.config;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;

// Mirror of AppointmentBookingWorkflow signal interface
// payment-service uses this to signal the running workflow
@WorkflowInterface
public interface AppointmentBookingWorkflowSignal {
    @SignalMethod
    void paymentCompleted(String paymentIntentId, boolean success);
}
```

### Step 7: Payment Controller + Webhook Handler

Create `payment-service/src/main/java/com/mediq/payment/controller/PaymentController.java`:

```java
package com.mediq.payment.controller;

import com.mediq.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final String webhookSecret;

    public PaymentController(
            PaymentService paymentService,
            @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret;
    }

    // Called by Temporal PaymentActivities
    @PostMapping("/payments/intent")
    public ResponseEntity<Map<String, String>> createPaymentIntent(
            @RequestBody Map<String, Object> request) throws Exception {

        var response = paymentService.createPaymentIntent(
            (String) request.get("appointmentId"),
            (String) request.get("patientId"),
            new BigDecimal(request.get("amount").toString()),
            (String) request.get("temporalWorkflowId")
        );

        return ResponseEntity.ok(Map.of(
            "paymentId", response.paymentId(),
            "clientSecret", response.clientSecret(),
            "paymentIntentId", response.paymentIntentId()
        ));
    }

    // Called by Stripe CLI webhook forwarder
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            // Verify webhook signature — prevents fake webhooks
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        log.info("Stripe webhook received: {}", event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent paymentIntent = (PaymentIntent) event
                    .getDataObjectDeserializer()
                    .getObject().orElseThrow();

                log.info("Payment succeeded: {}", paymentIntent.getId());
                paymentService.handleStripeWebhook(
                    paymentIntent.getId(), true, null);
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent paymentIntent = (PaymentIntent) event
                    .getDataObjectDeserializer()
                    .getObject().orElseThrow();

                String failureMsg = paymentIntent.getLastPaymentError() != null
                    ? paymentIntent.getLastPaymentError().getMessage()
                    : "Payment failed";

                log.warn("Payment failed: {} reason: {}",
                    paymentIntent.getId(), failureMsg);
                paymentService.handleStripeWebhook(
                    paymentIntent.getId(), false, failureMsg);
            }
            default -> log.debug("Unhandled Stripe event: {}", event.getType());
        }

        return ResponseEntity.ok("received");
    }
}
```

### Step 8: Payment Repository

Create `payment-service/src/main/java/com/mediq/payment/repository/PaymentRepository.java`:

```java
package com.mediq.payment.repository;

import com.mediq.payment.model.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<PaymentEntity> findByAppointmentId(UUID appointmentId);
}
```

### Step 9: Main Application Class

Create `payment-service/src/main/java/com/mediq/payment/PaymentServiceApplication.java`:

```java
package com.mediq.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

### Step 10: Dockerfile

Create `payment-service/Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/payment-service-1.0.0.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 11: Add to docker-compose.yml

```yaml
  # ── payment-service ────────────────────────────────────────────────────────
  payment-service:
    build:
      context: ./payment-service
      dockerfile: Dockerfile
    container_name: mediq-payment-service
    ports:
      - "8089:8089"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/mediq_payments
      DB_USERNAME: mediq
      DB_PASSWORD: mediq
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      STRIPE_SECRET_KEY: ${STRIPE_SECRET_KEY}
      STRIPE_WEBHOOK_SECRET: ${STRIPE_WEBHOOK_SECRET}
      TEMPORAL_HOST: temporal
      TEMPORAL_PORT: 7233
      JAEGER_ENDPOINT: http://jaeger:4318/v1/traces
    networks:
      - mediq-net
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      temporal:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
             "http://localhost:8089/actuator/health"]
      interval: 20s
      timeout: 5s
      retries: 5
      start_period: 45s
```

Create `.env` file in project root (never commit this to git):

```env
STRIPE_SECRET_KEY=sk_test_your_actual_key_here
STRIPE_WEBHOOK_SECRET=whsec_will_be_set_after_stripe_listen
```

Add `.env` to `.gitignore`:
```
.env
```

### Step 12: Add payment-service to KrakenD

Add to `helm/gateway/krakend/config/settings/hosts.json`:
```json
"payment_service": ["http://payment-service:8089"]
```

Add to `krakend/settings/hosts.json` (docker-compose version):
```json
"payment_service": ["http://payment-service:8089"]
```

Create `helm/gateway/krakend/config/partials/endpoint_payments.tmpl`:
```json
{
  "endpoint": "/api/v1/payments/intent",
  "method": "POST",
  "extra_config": {
    {{ template "auth_doctor_nurse_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/payments/intent",
    "host": ["{{ .hosts.payment_service }}"],
    "encoding": "json"
  }]
},
{
  "endpoint": "/api/v1/payments/{paymentId}",
  "method": "GET",
  "extra_config": {
    {{ template "auth_doctor_nurse_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/payments/{paymentId}",
    "host": ["{{ .hosts.payment_service }}"],
    "encoding": "json"
  }]
}
```

Add `endpoint_payments.tmpl` to both `krakend.tmpl` files and to ConfigMap.

### Step 13: Add Helmfile release for payment-service

Add to `helmfile.d/03-core.yaml`:
```yaml
  - name: mediq-payment-service
    chart: ./helm/services/payment-service
    namespace: mediq
    needs:
      - mediq/mediq-postgres
      - mediq/mediq-kafka
      - mediq/mediq-temporal
    values:
      - ./helm/services/payment-service/values.yaml
```

Create `helm/services/payment-service/` chart following same pattern as other services.

---

## Verification — End-to-End Stripe Flow

### 1. Start all services
```powershell
docker compose up --build
```

### 2. Start Stripe webhook forwarder (new PowerShell window)
```powershell
stripe listen --forward-to http://localhost:8089/webhooks/stripe
```

Copy the webhook signing secret shown:
```
> Ready! Your webhook signing secret is whsec_xxxx...
```

Update `.env` with STRIPE_WEBHOOK_SECRET and restart payment-service:
```powershell
docker compose restart payment-service
```

### 3. Book an appointment (triggers full Temporal Saga)
```powershell
curl -X POST http://localhost:8080/api/v1/appointments `
  -H "Content-Type: application/json" `
  -d '{
    "doctorId": "{doctorId}",
    "slotId": "{slotId}",
    "amount": 500.00
  }'
# Response: {"workflowId": "appointment-xxx", "status": "INITIATED"}
```

### 4. Watch Temporal UI
```
http://localhost:8088
→ Workflow: appointment-xxx
→ Status: AWAITING_PAYMENT (waiting for Stripe webhook)
```

### 5. Simulate Stripe payment success
```powershell
stripe payment_intents confirm {pi_xxx} `
  --payment-method pm_card_visa
```

OR trigger via Stripe CLI test:
```powershell
stripe trigger payment_intent.succeeded
```

### 6. Watch the Saga complete
```
Temporal UI:
  AWAITING_PAYMENT → CONFIRMING → NOTIFYING → CONFIRMED ✅

Stripe Dashboard (test mode):
  https://dashboard.stripe.com/test/payments
  → PaymentIntent: Succeeded ✅

payment-service DB:
  SELECT status FROM mediq_payments.payment WHERE appointment_id = '{id}';
  → SUCCEEDED

appointment-service DB:
  SELECT status FROM mediq_appointments.appointment WHERE id = '{id}';
  → CONFIRMED
```

### 7. Test payment failure (Saga compensation)
```powershell
# Use Stripe's decline card
stripe trigger payment_intent.payment_failed
```

```
Temporal UI:
  AWAITING_PAYMENT → COMPENSATING → FAILED ✅
  (slot automatically released back to AVAILABLE)

appointment-service DB:
  SELECT status FROM mediq_appointments.appointment;
  → CANCELLED
  SELECT status FROM mediq_appointments.appointment_slot;
  → AVAILABLE  ← slot released back ✅
```

## Commit
```powershell
git add .
git commit -m "feat(m5b): payment-service with Stripe test mode

- payment-service (port 8089) — new microservice
- Stripe PaymentIntent creation via Stripe Java SDK
- Webhook handler: payment_intent.succeeded/failed
- Sends Temporal signal to unblock waiting workflow
- Stripe webhook signature verification
- payment table with full payment lifecycle tracking
- Temporal integration: signal sent on webhook receipt
- .env for Stripe keys (not committed to git)
- docker-compose: stripe listen forwards webhooks locally
- KrakenD: /api/v1/payments/* endpoints added
- Helmfile: payment-service in 03-core layer"
```
