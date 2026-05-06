# mediq — Task M5a Bug Fix: Payment Idempotency in Temporal Saga

## Branch
```powershell
git checkout main
git pull origin main
git checkout -b fix/mediq-m5a-payment-idempotency
```

## Bugs Being Fixed

```
Bug 1 (CRITICAL): Temporal retry creates duplicate Stripe PaymentIntent
  Temporal retries PaymentActivities.createPaymentIntent() on failure
  Each retry calls Stripe → creates a NEW PaymentIntent
  Patient gets charged MULTIPLE TIMES ❌

Bug 2 (CRITICAL): No duplicate guard in payment-service
  payment-service has no check: "has this appointment already
  been charged?"
  Concurrent requests or retries → multiple PaymentIntents
  for same appointment ❌

Bug 3 (MEDIUM): Payment record created AFTER Stripe call
  If app crashes between Stripe success and DB save:
  → PaymentIntent exists in Stripe ✅
  → No record in DB ❌
  → Retry creates second PaymentIntent → duplicate charge ❌

Fix:
  Part 1: Stripe idempotency key on every PaymentIntent creation
  Part 2: Duplicate guard in payment-service before Stripe call
  Part 3: Save PENDING record BEFORE calling Stripe
```

## Files Modified

```
payment-service/src/main/java/com/mediq/payment/service/PaymentService.java
appointment-service/src/main/java/com/mediq/appointment/temporal/activity/PaymentActivitiesImpl.java
```

---

## Fix 1 — Stripe Idempotency Key

### Understanding the fix

```
Stripe supports idempotency keys on all API calls.
If you call Stripe twice with the same idempotency key:
  → Stripe returns the SAME PaymentIntent both times
  → No second charge created
  → Safe for retries ✅

Key design:
  "mediq-appt-{appointmentId}"
  
  appointmentId is unique per booking attempt
  Same appointment → same key → same PaymentIntent
  Different appointment → different key → new PaymentIntent
```

### File: `payment-service/src/main/java/com/mediq/payment/service/PaymentService.java`

Find the `createPaymentIntent` method.

Find this block:
```java
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
```

Replace with:
```java
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

// Stripe idempotency key — prevents duplicate PaymentIntents
// If Temporal retries this activity, Stripe returns same PaymentIntent
// appointmentId is unique per booking → safe idempotency key
RequestOptions idempotencyOptions = RequestOptions.builder()
    .setIdempotencyKey("mediq-appt-" + appointmentId)
    .build();

PaymentIntent paymentIntent = PaymentIntent.create(params, idempotencyOptions);
```

Add the import at the top of the file:
```java
import com.stripe.net.RequestOptions;
```

**Why this works:**
```
Temporal retry scenario:
  Attempt 1: key="mediq-appt-appt-123" → Stripe creates pi_abc ✅
             response lost in network timeout
  Attempt 2: key="mediq-appt-appt-123" → Stripe returns SAME pi_abc ✅
             no new PaymentIntent created
  Attempt 3: key="mediq-appt-appt-123" → Stripe returns SAME pi_abc ✅

Patient is never charged twice.
All retries get the same PaymentIntent. ✅
```

---

## Fix 2 — Duplicate Guard Before Stripe Call

### Understanding the fix

```
Even with Stripe idempotency key, we need a guard
in our own DB for two reasons:

Reason 1: Concurrent requests
  Two simultaneous requests for same appointment
  Both pass DB check (race condition)
  Both call Stripe with same key → Stripe handles it ✅
  But we get two DB records for same appointment ❌

Reason 2: Retry returns existing record
  Temporal retries createPaymentIntent
  DB already has the record from attempt 1
  We can return early without calling Stripe at all
  Faster + cheaper (fewer Stripe API calls)
```

### File: `payment-service/src/main/java/com/mediq/payment/service/PaymentService.java`

Find the beginning of `createPaymentIntent` method:

```java
@Transactional
public CreatePaymentIntentResponse createPaymentIntent(
        String appointmentId,
        String patientId,
        BigDecimal amount,
        String temporalWorkflowId) throws StripeException {

    // Amount in paise
    long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
```

Replace with:
```java
@Transactional
public CreatePaymentIntentResponse createPaymentIntent(
        String appointmentId,
        String patientId,
        BigDecimal amount,
        String temporalWorkflowId) throws StripeException {

    // ── Duplicate guard ───────────────────────────────────────────────────
    // Check if payment already exists for this appointment
    // Handles Temporal retries + concurrent requests
    Optional<PaymentEntity> existing =
        paymentRepository.findByAppointmentId(
            UUID.fromString(appointmentId));

    if (existing.isPresent()) {
        PaymentEntity existingPayment = existing.get();
        log.info("Duplicate payment request detected for " +
                 "appointmentId={} — returning existing PaymentIntent={}",
            appointmentId, existingPayment.getStripePaymentIntentId());

        // Return same response as original call
        // Temporal gets idempotent response — workflow continues safely
        return new CreatePaymentIntentResponse(
            existingPayment.getId().toString(),
            existingPayment.getStripeClientSecret(),
            existingPayment.getStripePaymentIntentId()
        );
    }

    // Amount in paise
    long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
```

---

## Fix 3 — Save PENDING Record BEFORE Stripe Call

### Understanding the fix

```
Current order (WRONG):
  1. Call Stripe → PaymentIntent created in Stripe ✅
  2. Save to DB → crash here ❌
  
  Result: PaymentIntent in Stripe, nothing in DB
  Retry: calls Stripe again (Stripe idempotency handles it ✅)
         but tries to save ANOTHER DB record ❌

Correct order:
  1. Save PENDING record to DB ✅
  2. Call Stripe → PaymentIntent created ✅
  3. Update DB record with Stripe details ✅

  If crash between 1 and 2:
    DB has PENDING record ✅
    Retry: duplicate guard finds PENDING record
    BUT: no Stripe PaymentIntent yet
    Need to handle this case

  Solution: check if existing record has stripePaymentIntentId
    If null → Stripe call not completed yet → proceed with Stripe call
    If not null → already done → return existing response
```

### File: `payment-service/src/main/java/com/mediq/payment/service/PaymentService.java`

Replace the entire `createPaymentIntent` method with this corrected version:

```java
@Transactional
public CreatePaymentIntentResponse createPaymentIntent(
        String appointmentId,
        String patientId,
        BigDecimal amount,
        String temporalWorkflowId) throws StripeException {

    // ── Step 1: Duplicate guard ───────────────────────────────────────────
    Optional<PaymentEntity> existing =
        paymentRepository.findByAppointmentId(
            UUID.fromString(appointmentId));

    if (existing.isPresent()) {
        PaymentEntity existingPayment = existing.get();

        // If Stripe call already completed → return existing response
        if (existingPayment.getStripePaymentIntentId() != null) {
            log.info("Returning existing PaymentIntent for " +
                     "appointmentId={}", appointmentId);
            return new CreatePaymentIntentResponse(
                existingPayment.getId().toString(),
                existingPayment.getStripeClientSecret(),
                existingPayment.getStripePaymentIntentId()
            );
        }

        // If PENDING (Stripe call not completed in previous attempt)
        // Fall through to Stripe call — idempotency key handles dedup
        log.info("Found PENDING payment for appointmentId={} — " +
                 "retrying Stripe call with idempotency key", appointmentId);
    }

    // ── Step 2: Save PENDING record BEFORE calling Stripe ────────────────
    // If app crashes after this and before Stripe call:
    //   → record stays PENDING → next retry finds it → retries Stripe call
    // If app crashes after Stripe call but before DB update:
    //   → Stripe idempotency key ensures same PaymentIntent returned
    PaymentEntity payment = existing.orElseGet(() -> {
        PaymentEntity newPayment = new PaymentEntity();
        newPayment.setAppointmentId(UUID.fromString(appointmentId));
        newPayment.setPatientId(UUID.fromString(patientId));
        newPayment.setAmount(amount);
        newPayment.setCurrency(currency);
        newPayment.setStatus(PaymentStatus.PENDING);
        newPayment.setTemporalWorkflowId(temporalWorkflowId);
        return paymentRepository.save(newPayment);
    });

    log.info("Calling Stripe API for appointmentId={} paymentId={}",
        appointmentId, payment.getId());

    // ── Step 3: Call Stripe with idempotency key ──────────────────────────
    long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

    PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
        .setAmount(amountInPaise)
        .setCurrency(currency)
        .putMetadata("appointmentId", appointmentId)
        .putMetadata("patientId", patientId)
        .putMetadata("temporalWorkflowId", temporalWorkflowId)
        .putMetadata("mediqPaymentId", payment.getId().toString())
        .setAutomaticPaymentMethods(
            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                .setEnabled(true)
                .build())
        .build();

    // Idempotency key = same PaymentIntent returned on retry
    RequestOptions options = RequestOptions.builder()
        .setIdempotencyKey("mediq-appt-" + appointmentId)
        .build();

    PaymentIntent paymentIntent;
    try {
        paymentIntent = PaymentIntent.create(params, options);
        log.info("Stripe PaymentIntent created: {} for appointmentId: {}",
            paymentIntent.getId(), appointmentId);
    } catch (StripeException e) {
        // Update record to FAILED — Temporal will retry
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(e.getMessage());
        paymentRepository.save(payment);
        log.error("Stripe call failed for appointmentId={}: {}",
            appointmentId, e.getMessage());
        throw e;  // rethrow — Temporal retries the activity
    }

    // ── Step 4: Update record with Stripe details ─────────────────────────
    payment.setStripePaymentIntentId(paymentIntent.getId());
    payment.setStripeClientSecret(paymentIntent.getClientSecret());
    payment.setStatus(PaymentStatus.PROCESSING);
    paymentRepository.save(payment);

    return new CreatePaymentIntentResponse(
        payment.getId().toString(),
        paymentIntent.getClientSecret(),
        paymentIntent.getId()
    );
}
```

---

## Fix 4 — PaymentActivitiesImpl passes workflowId

### Understanding the fix

```
payment-service needs temporalWorkflowId to:
  1. Send signal back to the correct workflow
  2. Store in payment record for tracing

Currently PaymentActivitiesImpl does NOT pass workflowId
to payment-service. It needs to be included in the request.
```

### File: `appointment-service/src/main/java/com/mediq/appointment/temporal/activity/PaymentActivitiesImpl.java`

Find:
```java
@Override
public String createPaymentIntent(String appointmentId,
                                  String patientId,
                                  BigDecimal amount) {
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
```

Replace with:
```java
@Override
public String createPaymentIntent(String appointmentId,
                                  String patientId,
                                  BigDecimal amount) {
    // Get current workflow ID — used by payment-service to signal back
    String workflowId = Activity.getExecutionContext()
        .getInfo().getWorkflowId();

    Map<String, Object> request = Map.of(
        "appointmentId", appointmentId,
        "patientId", patientId,
        "amount", amount,
        "currency", "inr",
        "temporalWorkflowId", workflowId   // ← added
    );

    Map response = restTemplate.postForObject(
        paymentServiceUrl + "/payments/intent",
        request,
        Map.class);

    return (String) response.get("clientSecret");
}
```

Add import:
```java
import io.temporal.activity.Activity;
```

---

## Fix 5 — Unique constraint on appointment_id in payment table

### Understanding the fix

```
Without a DB-level unique constraint on appointment_id:
  Two concurrent threads could both pass the duplicate guard
  (race condition between check and insert)
  Both insert records for same appointment
  → Two charges possible

DB unique constraint = last line of defense
  Even if application logic fails → DB rejects the second insert
  → application catches DataIntegrityViolationException
  → returns existing record
```

### File: `payment-service/src/main/resources/db/migration/V1__create_payment_schema.sql`

Find:
```sql
CREATE TABLE payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID NOT NULL,
```

Replace with:
```sql
CREATE TABLE payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID NOT NULL UNIQUE,   -- ← add UNIQUE constraint
```

Also add explicit index (already has UNIQUE which creates one, but
explicit index is clearer):
```sql
-- Already created by UNIQUE constraint above
-- Explicit name for clarity in monitoring/explain plans
CREATE UNIQUE INDEX idx_payment_appointment_unique
    ON payment(appointment_id);
```

### Handle DataIntegrityViolationException in PaymentService

Add exception handler around the save in `createPaymentIntent`:

```java
// ── Step 2: Save PENDING record (with race condition protection) ──────────
PaymentEntity payment;
try {
    payment = existing.orElseGet(() -> {
        PaymentEntity newPayment = new PaymentEntity();
        newPayment.setAppointmentId(UUID.fromString(appointmentId));
        newPayment.setPatientId(UUID.fromString(patientId));
        newPayment.setAmount(amount);
        newPayment.setCurrency(currency);
        newPayment.setStatus(PaymentStatus.PENDING);
        newPayment.setTemporalWorkflowId(temporalWorkflowId);
        return paymentRepository.save(newPayment);
    });
} catch (DataIntegrityViolationException e) {
    // Race condition: another thread inserted first
    // Fetch the existing record and proceed
    log.warn("Race condition detected for appointmentId={} — " +
             "fetching existing record", appointmentId);
    payment = paymentRepository
        .findByAppointmentId(UUID.fromString(appointmentId))
        .orElseThrow(() -> new RuntimeException(
            "Payment record disappeared: " + appointmentId));

    // If Stripe already called → return existing response
    if (payment.getStripePaymentIntentId() != null) {
        return new CreatePaymentIntentResponse(
            payment.getId().toString(),
            payment.getStripeClientSecret(),
            payment.getStripePaymentIntentId()
        );
    }
}
```

Add import:
```java
import org.springframework.dao.DataIntegrityViolationException;
```

---

## Summary of All Fixes

```
Fix 1 — Stripe idempotency key:
  RequestOptions with key="mediq-appt-{appointmentId}"
  Stripe returns same PaymentIntent on retry
  Prevents duplicate Stripe charges ✅

Fix 2 — Duplicate guard in payment-service:
  Check DB before calling Stripe
  If record exists AND has stripePaymentIntentId → return it
  If record exists but no stripePaymentIntentId → retry Stripe call
  Temporal retries get idempotent response ✅

Fix 3 — Save PENDING record BEFORE Stripe call:
  DB record saved → then Stripe called → then DB updated
  If crash between steps → retry finds PENDING record → safe
  No orphaned Stripe PaymentIntents without DB records ✅

Fix 4 — Pass workflowId from Temporal activity:
  PaymentActivitiesImpl passes workflowId in request body
  payment-service uses it to signal Temporal on webhook
  Stored in payment record for tracing ✅

Fix 5 — Unique constraint on appointment_id:
  DB-level last defense against race conditions
  DataIntegrityViolationException caught gracefully
  Returns existing record instead of crashing ✅
```

---

## Verification

### 1. Build payment-service and appointment-service
```powershell
docker build -t mediq/payment-service:latest ./payment-service
docker build -t mediq/appointment-service:latest ./appointment-service
docker compose up --build
```

### 2. Test idempotency — same appointment, multiple payment requests
```powershell
# Book appointment — get workflowId
$workflowId = "appointment-test-001"

# Call payment/intent 3 times with same appointmentId
$body = '{"appointmentId":"appt-123","patientId":"pat-456","amount":500,"temporalWorkflowId":"wf-001"}'

curl -X POST http://localhost:8089/payments/intent `
  -H "Content-Type: application/json" -d $body

curl -X POST http://localhost:8089/payments/intent `
  -H "Content-Type: application/json" -d $body

curl -X POST http://localhost:8089/payments/intent `
  -H "Content-Type: application/json" -d $body

# Expected: ALL 3 return the SAME paymentIntentId
# Check logs: 2nd and 3rd show "Returning existing PaymentIntent"
docker logs mediq-payment-service | grep "Returning existing\|Duplicate\|PaymentIntent created"
```

### 3. Verify only ONE record in DB
```powershell
docker exec -it mediq-postgres psql -U mediq -d mediq_payments
mediq_payments=# SELECT id, appointment_id, stripe_payment_intent_id, status
                 FROM mediq_payments.payment
                 WHERE appointment_id = 'appt-123';
# Expected: exactly ONE row ✅
```

### 4. Verify Stripe dashboard shows ONE PaymentIntent
```
https://dashboard.stripe.com/test/payments
→ Only ONE PaymentIntent for this appointment ✅
→ NOT multiple with same metadata
```

### 5. Verify unique constraint
```powershell
# Try to manually insert duplicate (should fail)
docker exec -it mediq-postgres psql -U mediq -d mediq_payments
mediq_payments=# INSERT INTO mediq_payments.payment
  (appointment_id, patient_id, amount, status)
  VALUES ('appt-123', 'pat-456', 500, 'PENDING');
# Expected: ERROR: duplicate key value violates unique constraint ✅
```

---

## Commit
```powershell
git add .
git commit -m "fix(m5a): payment idempotency in Temporal Saga

Bug 1: Stripe idempotency key prevents duplicate PaymentIntents
  RequestOptions.setIdempotencyKey('mediq-appt-{appointmentId}')
  Temporal retries → same Stripe PaymentIntent returned always

Bug 2: Duplicate guard before Stripe call
  Checks DB first → returns existing if found
  Handles Temporal retries + concurrent requests

Bug 3: PENDING record saved BEFORE Stripe call
  Crash-safe ordering: save → call Stripe → update
  Retry finds PENDING record → continues safely

Bug 4: workflowId passed from Temporal activity
  Activity.getExecutionContext().getInfo().getWorkflowId()
  payment-service can signal correct workflow on webhook

Bug 5: UNIQUE constraint on payment.appointment_id
  DB-level guard against race conditions
  DataIntegrityViolationException caught → returns existing record"
```
