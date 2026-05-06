# mediq — Task M5a: Temporal Saga Orchestrator

## Branch
```powershell
git checkout main
git pull origin main
git checkout -b feature/mediq-m5a-temporal-saga
```

## What This Task Builds
```
Temporal = durable workflow engine for Saga orchestration
Replaces choreography-based Saga in appointment-service
with a centrally orchestrated, crash-safe workflow

Before (choreography):
  Services react to events independently
  Hard to see full workflow state
  No timeout handling
  Hard to debug "where is this booking stuck?"

After (Temporal orchestration):
  One AppointmentBookingWorkflow manages the entire flow
  Visual dashboard shows every booking's exact state
  Automatic retry per step with configurable backoff
  Timeout: "if payment not received in 10min → cancel"
  Crash recovery: workflow resumes from exact step
```

## Architecture

```
Patient calls POST /api/v1/appointments
  ↓
appointment-service starts Temporal workflow
  ↓
Temporal Worker executes workflow steps:
  Step 1: lockSlot (appointment-service activity)
  Step 2: createPaymentIntent (payment-service activity)
  Step 3: waitForPayment (wait for Stripe webhook signal)
  Step 4: confirmAppointment (appointment-service activity)
  Step 5: sendNotification (notification-service activity)

If Step 2 fails → compensation: releaseSlot
If payment times out → compensation: releaseSlot + cancel
```

## New Files

```
appointment-service/src/main/java/com/mediq/appointment/
  temporal/
    workflow/
      AppointmentBookingWorkflow.java      ← workflow interface
      AppointmentBookingWorkflowImpl.java  ← workflow implementation
    activity/
      AppointmentActivities.java           ← activity interface
      AppointmentActivitiesImpl.java       ← calls appointment-service DB
      PaymentActivities.java               ← activity interface
      PaymentActivitiesImpl.java           ← calls payment-service REST
      NotificationActivities.java          ← activity interface
      NotificationActivitiesImpl.java      ← calls notification-service REST
    worker/
      AppointmentWorker.java               ← registers workflow + activities
    config/
      TemporalConfig.java                  ← Temporal client bean
```

## Step 1: Add Temporal dependency to appointment-service pom.xml

```xml
<!-- Temporal SDK -->
<dependency>
    <groupId>io.temporal</groupId>
    <artifactId>temporal-sdk</artifactId>
    <version>1.24.1</version>
</dependency>

<!-- Temporal Spring Boot starter -->
<dependency>
    <groupId>io.temporal</groupId>
    <artifactId>temporal-spring-boot-starter-alpha</artifactId>
    <version>1.24.1</version>
</dependency>
```

## Step 2: Add Temporal server to docker-compose.yml

Add BEFORE the `user-service` service:

```yaml
  # ── Temporal Server ────────────────────────────────────────────────────────
  temporal:
    image: temporalio/auto-setup:1.24.2
    container_name: mediq-temporal
    ports:
      - "7233:7233"       # Temporal gRPC API (workers connect here)
    environment:
      DB: postgresql
      DB_PORT: 5432
      POSTGRES_USER: mediq
      POSTGRES_PWD: mediq
      POSTGRES_SEEDS: postgres
      DYNAMIC_CONFIG_FILE_PATH: config/dynamicconfig/development-sql.yaml
    networks:
      - mediq-net
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "tctl", "--address", "temporal:7233", "cluster", "health"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  # ── Temporal Web UI ────────────────────────────────────────────────────────
  temporal-ui:
    image: temporalio/ui:2.26.2
    container_name: mediq-temporal-ui
    ports:
      - "8088:8080"       # Temporal dashboard: http://localhost:8088
    environment:
      TEMPORAL_ADDRESS: temporal:7233
      TEMPORAL_CORS_ORIGINS: http://localhost:8088
    networks:
      - mediq-net
    depends_on:
      temporal:
        condition: service_healthy
```

Also add Temporal DB in postgres init script `scripts/postgres-init.sql`:

```sql
-- Add this at the end of existing postgres-init.sql
CREATE DATABASE temporal;
CREATE DATABASE temporal_visibility;
GRANT ALL PRIVILEGES ON DATABASE temporal TO mediq;
GRANT ALL PRIVILEGES ON DATABASE temporal_visibility TO mediq;
```

## Step 3: Temporal Configuration

Create `appointment-service/src/main/java/com/mediq/appointment/temporal/config/TemporalConfig.java`:

```java
package com.mediq.appointment.temporal.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    @Value("${temporal.host:localhost}:${temporal.port:7233}")
    private String temporalTarget;

    @Value("${temporal.namespace:mediq-appointments}")
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

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }
}
```

Add to `appointment-service/src/main/resources/application.properties`:

```properties
# ── Temporal ──────────────────────────────────────────────────────────────────
temporal.host=${TEMPORAL_HOST:localhost}
temporal.port=${TEMPORAL_PORT:7233}
temporal.namespace=mediq-appointments
temporal.task-queue=appointment-booking-queue
```

Add to docker-compose `appointment-service` environment:

```yaml
TEMPORAL_HOST: temporal
TEMPORAL_PORT: 7233
```

## Step 4: Workflow Interface

Create `appointment-service/src/main/java/com/mediq/appointment/temporal/workflow/AppointmentBookingWorkflow.java`:

```java
package com.mediq.appointment.temporal.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AppointmentBookingWorkflow {

    @WorkflowMethod
    BookingResult bookAppointment(BookingRequest request);

    // Signal received from payment-service when Stripe webhook fires
    @SignalMethod
    void paymentCompleted(String paymentIntentId, boolean success);

    // Query current state of this booking workflow
    @QueryMethod
    String getBookingStatus();
}
```

Create `BookingRequest.java` and `BookingResult.java` in same package:

```java
package com.mediq.appointment.temporal.workflow;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record BookingRequest(
    UUID patientId,
    UUID doctorId,
    UUID slotId,
    BigDecimal amount,
    String patientEmail
) implements Serializable {}

public record BookingResult(
    String appointmentId,
    String status,
    String message
) implements Serializable {
    public static BookingResult success(String appointmentId) {
        return new BookingResult(appointmentId, "CONFIRMED", "Appointment confirmed");
    }
    public static BookingResult failed(String reason) {
        return new BookingResult(null, "FAILED", reason);
    }
}
```

## Step 5: Workflow Implementation — The Saga Orchestrator

Create `AppointmentBookingWorkflowImpl.java`:

```java
package com.mediq.appointment.temporal.workflow;

import com.mediq.appointment.temporal.activity.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicString;

public class AppointmentBookingWorkflowImpl
        implements AppointmentBookingWorkflow {

    private static final Logger log =
        Workflow.getLogger(AppointmentBookingWorkflowImpl.class);

    // Activity stubs — Temporal proxies these to actual implementations
    private final AppointmentActivities appointmentActivities =
        Workflow.newActivityStub(AppointmentActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .build())
                .build());

    private final PaymentActivities paymentActivities =
        Workflow.newActivityStub(PaymentActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .build())
                .build());

    private final NotificationActivities notificationActivities =
        Workflow.newActivityStub(NotificationActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .build())
                .build());

    // State managed by signals
    private String currentStatus = "INITIATED";
    private boolean paymentReceived = false;
    private boolean paymentSuccess = false;
    private String paymentIntentId;

    @Override
    public BookingResult bookAppointment(BookingRequest request) {
        String appointmentId = null;

        // ── Step 1: Lock the slot ─────────────────────────────────────────
        currentStatus = "LOCKING_SLOT";
        log.info("Locking slot: {}", request.slotId());

        try {
            appointmentId = appointmentActivities.lockSlot(
                request.slotId().toString(),
                request.patientId().toString(),
                request.doctorId().toString());
        } catch (Exception e) {
            currentStatus = "FAILED";
            return BookingResult.failed("Slot not available: " + e.getMessage());
        }

        // ── Step 2: Create Stripe PaymentIntent ───────────────────────────
        currentStatus = "CREATING_PAYMENT";
        log.info("Creating payment intent for appointmentId: {}", appointmentId);

        String paymentClientSecret;
        try {
            paymentClientSecret = paymentActivities.createPaymentIntent(
                appointmentId,
                request.patientId().toString(),
                request.amount());
        } catch (Exception e) {
            // Payment creation failed — compensate by releasing slot
            currentStatus = "COMPENSATING";
            appointmentActivities.releaseSlot(request.slotId().toString());
            return BookingResult.failed("Payment setup failed: " + e.getMessage());
        }

        // ── Step 3: Wait for Stripe webhook (max 10 minutes) ─────────────
        currentStatus = "AWAITING_PAYMENT";
        log.info("Waiting for payment confirmation, appointmentId: {}", appointmentId);

        // Temporal waits here — workflow is suspended, no thread blocked
        // Resumes when paymentCompleted() signal is received OR timeout
        boolean paymentConfirmed = Workflow.await(
            Duration.ofMinutes(10),           // timeout after 10 minutes
            () -> this.paymentReceived);      // condition to resume

        if (!paymentConfirmed || !paymentSuccess) {
            // Timeout or payment failed — compensate
            currentStatus = "COMPENSATING";
            log.warn("Payment failed/timed out for appointmentId: {}", appointmentId);

            appointmentActivities.cancelAppointment(
                appointmentId,
                "Payment not completed within 10 minutes");
            appointmentActivities.releaseSlot(request.slotId().toString());

            return BookingResult.failed("Payment failed or timed out");
        }

        // ── Step 4: Confirm appointment ───────────────────────────────────
        currentStatus = "CONFIRMING";
        appointmentActivities.confirmAppointment(appointmentId, paymentIntentId);

        // ── Step 5: Send notification (non-critical) ──────────────────────
        currentStatus = "NOTIFYING";
        try {
            notificationActivities.sendAppointmentConfirmation(
                request.patientEmail(),
                appointmentId,
                request.doctorId().toString());
        } catch (Exception e) {
            // Notification failure does NOT fail the booking
            log.warn("Notification failed for appointmentId: {}, error: {}",
                appointmentId, e.getMessage());
        }

        currentStatus = "CONFIRMED";
        return BookingResult.success(appointmentId);
    }

    @Override
    public void paymentCompleted(String paymentIntentId, boolean success) {
        // This signal is sent by payment-service when Stripe webhook fires
        this.paymentIntentId = paymentIntentId;
        this.paymentSuccess = success;
        this.paymentReceived = true;  // unblocks Workflow.await() above
        log.info("Payment signal received: paymentIntentId={}, success={}",
            paymentIntentId, success);
    }

    @Override
    public String getBookingStatus() {
        return currentStatus;
    }
}
```

## Step 6: Activity Interfaces

Create `AppointmentActivities.java`:

```java
package com.mediq.appointment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AppointmentActivities {

    @ActivityMethod
    String lockSlot(String slotId, String patientId, String doctorId);

    @ActivityMethod
    void releaseSlot(String slotId);

    @ActivityMethod
    void confirmAppointment(String appointmentId, String paymentIntentId);

    @ActivityMethod
    void cancelAppointment(String appointmentId, String reason);
}
```

Create `PaymentActivities.java`:

```java
package com.mediq.appointment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.math.BigDecimal;

@ActivityInterface
public interface PaymentActivities {

    @ActivityMethod
    String createPaymentIntent(String appointmentId,
                               String patientId,
                               BigDecimal amount);
}
```

Create `NotificationActivities.java`:

```java
package com.mediq.appointment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NotificationActivities {

    @ActivityMethod
    void sendAppointmentConfirmation(String patientEmail,
                                     String appointmentId,
                                     String doctorId);
}
```

## Step 7: Activity Implementations

Create `AppointmentActivitiesImpl.java`:

```java
package com.mediq.appointment.temporal.activity;

import com.mediq.appointment.model.*;
import com.mediq.appointment.repository.*;
import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@ActivityImpl(taskQueues = "appointment-booking-queue")
public class AppointmentActivitiesImpl implements AppointmentActivities {

    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentActivitiesImpl(
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    @Transactional
    public String lockSlot(String slotId, String patientId, String doctorId) {
        AppointmentSlotEntity slot = slotRepository
            .findByIdForUpdate(UUID.fromString(slotId))
            .orElseThrow(() -> Activity.wrap(
                new RuntimeException("Slot not found: " + slotId)));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw Activity.wrap(
                new RuntimeException("Slot not available: " + slotId));
        }

        // Lock the slot
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        // Create appointment in PENDING_PAYMENT state
        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setSlot(slot);
        appointment.setPatientId(UUID.fromString(patientId));
        appointment.setDoctorId(UUID.fromString(doctorId));
        appointment.setStatus(AppointmentStatus.PENDING_PAYMENT);
        appointmentRepository.save(appointment);

        return appointment.getId().toString();
    }

    @Override
    @Transactional
    public void releaseSlot(String slotId) {
        slotRepository.findById(UUID.fromString(slotId))
            .ifPresent(slot -> {
                slot.setStatus(SlotStatus.AVAILABLE);
                slotRepository.save(slot);
            });
    }

    @Override
    @Transactional
    public void confirmAppointment(String appointmentId, String paymentIntentId) {
        appointmentRepository.findById(UUID.fromString(appointmentId))
            .ifPresent(appt -> {
                appt.setStatus(AppointmentStatus.CONFIRMED);
                appt.setConfirmedAt(Instant.now());
                appt.setNotes("Payment: " + paymentIntentId);
                appointmentRepository.save(appt);
            });
    }

    @Override
    @Transactional
    public void cancelAppointment(String appointmentId, String reason) {
        appointmentRepository.findById(UUID.fromString(appointmentId))
            .ifPresent(appt -> {
                appt.setStatus(AppointmentStatus.CANCELLED);
                appt.setCancelledAt(Instant.now());
                appt.setCancellationReason(reason);
                appointmentRepository.save(appt);
            });
    }
}
```

Create `PaymentActivitiesImpl.java`:

```java
package com.mediq.appointment.temporal.activity;

import io.temporal.spring.boot.ActivityImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
@ActivityImpl(taskQueues = "appointment-booking-queue")
public class PaymentActivitiesImpl implements PaymentActivities {

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    public PaymentActivitiesImpl(
            @Value("${mediq.payment-service.url:http://payment-service:8089}")
            String paymentServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.paymentServiceUrl = paymentServiceUrl;
    }

    @Override
    public String createPaymentIntent(String appointmentId,
                                      String patientId,
                                      BigDecimal amount) {
        // Calls payment-service REST API to create Stripe PaymentIntent
        Map<String, Object> request = Map.of(
            "appointmentId", appointmentId,
            "patientId", patientId,
            "amount", amount,
            "currency", "inr"
        );

        Map response = restTemplate.postForObject(
            paymentServiceUrl + "/payments/intent",
            request,
            Map.class);

        return (String) response.get("clientSecret");
    }
}
```

Create `NotificationActivitiesImpl.java`:

```java
package com.mediq.appointment.temporal.activity;

import io.temporal.spring.boot.ActivityImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ActivityImpl(taskQueues = "appointment-booking-queue")
public class NotificationActivitiesImpl implements NotificationActivities {

    private final RestTemplate restTemplate;
    private final String notificationServiceUrl;

    public NotificationActivitiesImpl(
            @Value("${mediq.notification-service.url:http://notification-service:8085}")
            String notificationServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Override
    public void sendAppointmentConfirmation(String patientEmail,
                                            String appointmentId,
                                            String doctorId) {
        Map<String, String> request = Map.of(
            "patientEmail", patientEmail,
            "appointmentId", appointmentId,
            "doctorId", doctorId,
            "type", "APPOINTMENT_CONFIRMED"
        );
        restTemplate.postForObject(
            notificationServiceUrl + "/notifications/send",
            request, Void.class);
    }
}
```

## Step 8: Temporal Worker Registration

Create `AppointmentWorker.java`:

```java
package com.mediq.appointment.temporal.worker;

import com.mediq.appointment.temporal.activity.*;
import com.mediq.appointment.temporal.workflow.*;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppointmentWorker {

    private static final Logger log = LoggerFactory.getLogger(AppointmentWorker.class);

    private final WorkerFactory workerFactory;
    private final AppointmentActivitiesImpl appointmentActivities;
    private final PaymentActivitiesImpl paymentActivities;
    private final NotificationActivitiesImpl notificationActivities;
    private final String taskQueue;

    public AppointmentWorker(
            WorkerFactory workerFactory,
            AppointmentActivitiesImpl appointmentActivities,
            PaymentActivitiesImpl paymentActivities,
            NotificationActivitiesImpl notificationActivities,
            @Value("${temporal.task-queue}") String taskQueue) {
        this.workerFactory = workerFactory;
        this.appointmentActivities = appointmentActivities;
        this.paymentActivities = paymentActivities;
        this.notificationActivities = notificationActivities;
        this.taskQueue = taskQueue;
    }

    @PostConstruct
    public void startWorker() {
        Worker worker = workerFactory.newWorker(taskQueue);

        // Register workflow implementation
        worker.registerWorkflowImplementationTypes(
            AppointmentBookingWorkflowImpl.class);

        // Register activity implementations
        worker.registerActivitiesImplementations(
            appointmentActivities,
            paymentActivities,
            notificationActivities);

        workerFactory.start();
        log.info("Temporal worker started on task queue: {}", taskQueue);
    }
}
```

## Step 9: Update AppointmentController to Start Workflow

Update `AppointmentController.java` — replace direct service call with Temporal workflow start:

```java
@PostMapping("/appointments")
public ResponseEntity<Map<String, Object>> bookAppointment(
        @Valid @RequestBody BookAppointmentRequest request) {

    UserContext ctx = UserContextHolder.get();
    UUID patientId = UUID.fromString(ctx.userId());

    // Build workflow request
    BookingRequest bookingRequest = new BookingRequest(
        patientId,
        request.doctorId(),
        request.slotId(),
        request.amount(),
        ctx.email()          // from JWT claim X-User-Email
    );

    // Start Temporal workflow
    // workflowId = appointmentId so we can signal it later
    String workflowId = "appointment-" + UUID.randomUUID();

    AppointmentBookingWorkflow workflow = workflowClient.newWorkflowStub(
        AppointmentBookingWorkflow.class,
        WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(taskQueue)
            .setWorkflowExecutionTimeout(Duration.ofMinutes(15))
            .build());

    // Start async — return immediately to client
    WorkflowClient.start(workflow::bookAppointment, bookingRequest);

    return ResponseEntity.accepted().body(Map.of(
        "workflowId", workflowId,
        "status", "INITIATED",
        "message", "Booking started, awaiting payment"
    ));
}

@GetMapping("/appointments/{workflowId}/status")
public ResponseEntity<Map<String, String>> getStatus(
        @PathVariable String workflowId) {
    // Query workflow state
    AppointmentBookingWorkflow workflow = workflowClient.newWorkflowStub(
        AppointmentBookingWorkflow.class, workflowId);

    return ResponseEntity.ok(Map.of(
        "workflowId", workflowId,
        "status", workflow.getBookingStatus()
    ));
}
```

## Step 10: Add Temporal to Helm chart

Add to `helmfile.d/01-infrastructure.yaml`:

```yaml
  - name: mediq-temporal
    chart: ./helm/infrastructure/temporal
    namespace: mediq
    needs:
      - mediq/mediq-postgres
    values:
      - ./helm/infrastructure/temporal/values.yaml
```

Create `helm/infrastructure/temporal/Chart.yaml`:
```yaml
apiVersion: v2
name: mediq-temporal
description: Temporal workflow server for mediq
version: 0.1.0
```

Create `helm/infrastructure/temporal/values.yaml`:
```yaml
image: temporalio/auto-setup:1.24.2
uiImage: temporalio/ui:2.26.2
port: 7233
uiPort: 8088
uiNodePort: 30088
```

Create `helm/infrastructure/temporal/templates/temporal.yaml` — Deployment + Service for both temporal and temporal-ui following the same pattern as other infrastructure charts.

## Verification

### 1. Start everything
```powershell
docker compose up --build
```

### 2. Check Temporal server is healthy
```powershell
docker logs mediq-temporal | grep "Temporal server" | tail -5
# Expected: "Temporal server started"
```

### 3. Open Temporal Web UI
```
http://localhost:8088
→ Namespace: mediq-appointments
→ Should show no workflows yet (empty)
```

### 4. Book an appointment (triggers workflow)
```powershell
curl -X POST http://localhost:8080/api/v1/appointments `
  -H "Content-Type: application/json" `
  -H "X-User-Id: {patientId}" `
  -H "X-User-Role: PATIENT" `
  -d '{
    "doctorId": "{doctorId}",
    "slotId": "{slotId}",
    "amount": 500.00
  }'
# Expected: {"workflowId": "appointment-xxx", "status": "INITIATED"}
```

### 5. Watch workflow in Temporal UI
```
http://localhost:8088
→ See workflow "appointment-xxx" in RUNNING state
→ Click it → see Step 1 (lockSlot) completed
→ See Step 3 (awaiting payment) — WAITING for signal
→ Current step visible in real time
```

### 6. Check workflow status via API
```powershell
curl http://localhost:8080/api/v1/appointments/appointment-xxx/status
# Expected: {"status": "AWAITING_PAYMENT"}
```

### 7. Simulate payment signal (before payment-service is built)
```powershell
# Use Temporal CLI to send payment signal manually
docker exec -it mediq-temporal tctl workflow signal \
  --workflow_id appointment-xxx \
  --name paymentCompleted \
  --input '{"paymentIntentId":"pi_test_123","success":true}'

# Watch workflow complete in UI
# Status changes: AWAITING_PAYMENT → CONFIRMING → NOTIFYING → CONFIRMED
```

## Commit
```powershell
git add .
git commit -m "feat(m5a): Temporal Saga orchestrator for appointment booking

- Temporal server + UI in docker-compose
- AppointmentBookingWorkflow with 5 steps:
  lockSlot → createPaymentIntent → awaitPayment (signal)
  → confirmAppointment → sendNotification
- Saga compensation: payment failure releases slot automatically
- Workflow.await() with 10min timeout (no thread blocked)
- Temporal Web UI at http://localhost:8088
- WorkerFactory registers workflow + 3 activity implementations
- GET /appointments/{workflowId}/status queries workflow state
- Temporal added to Helm infrastructure layer"
```
