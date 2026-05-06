# mediq — Distributed Tracing Fixes

## Branch
```powershell
# Already on feature/mediq-m4-advanced (or whatever your current branch is)
# Create a fix branch from it
git checkout feature/mediq-m4-advanced
git checkout -b fix/distributed-tracing
```

## Bugs Being Fixed

```
Bug 1 (CRITICAL): KrakenD has no telemetry config
  → KrakenD generates zero spans
  → Traces from services are DISCONNECTED in Jaeger
  → No end-to-end waterfall possible

Bug 2 (CRITICAL): Inconsistent @Value property key across services
  → Some services: ${mediq.tracing.endpoint}
  → Others:        ${management.otlp.tracing.endpoint}
  → All should use the Spring Boot standard key

Bug 3 (MEDIUM): Manual TracingConfig bean conflicts with Spring Boot autoconfiguration
  → Spring Boot 3.x auto-creates OtlpHttpSpanExporter when property is set
  → Manual bean definition = potential BeanDefinitionOverrideException
  → Fix: delete TracingConfig.java from all services, let Spring Boot handle it

Bug 4 (MINOR): krakend.tmpl name still says "TrueCare"
```

---

## Fix 1 — Add OpenTelemetry to KrakenD

### Why this is the most important fix

```
Without this fix:
  Client → KrakenD → user-service → Kafka

  Jaeger shows:
    Trace A: user-service span (5ms)       ← isolated, no parent
    Trace B: notification-service span (3ms) ← isolated, no parent

  You cannot see: how long KrakenD took, which service is slow,
  the full request journey in one view.

With this fix:
  Jaeger shows ONE trace with waterfall:
    KrakenD        0ms → 250ms  (total)
    user-service   5ms → 230ms  (225ms)
      DB query     6ms → 50ms   (44ms — fast)
      Kafka pub    51ms → 228ms (177ms — SLOW ← found it)

  THIS is the value of distributed tracing.
```

**File:** `krakend/krakend.tmpl`

Find the `extra_config` section:
```json
"extra_config": {
  "router": {
    "return_error_msg": true
  }
},
```

Replace it with:
```json
"extra_config": {
  "router": {
    "return_error_msg": true
  },
  "telemetry/opentelemetry": {
    "service_name": "mediq-krakend",
    "service_version": "1.0.0",
    "exporters": {
      "otlp": [
        {
          "name": "jaeger-otlp",
          "host": "jaeger",
          "port": 4317,
          "use_grpc": true,
          "disable_metrics": false,
          "disable_traces": false
        }
      ]
    },
    "layers": {
      "global": {
        "disable_metrics": false,
        "disable_traces": false,
        "disable_propagation": false
      },
      "proxy": {
        "disable_metrics": false,
        "disable_traces": false
      },
      "backend": {
        "metrics": {
          "disable_stage": false,
          "round_trip": true,
          "read_payload": true,
          "detailed_connection": true,
          "static_attributes": []
        },
        "traces": {
          "disable_stage": false,
          "round_trip": true,
          "read_payload": true,
          "detailed_connection": true,
          "static_attributes": []
        }
      }
    }
  }
},
```

**Also fix Bug 4 — update the name field in krakend.tmpl:**
```json
"name": "mediq API Gateway — Local Dev",
```

---

## Fix 2 — Standardise TracingConfig property key

### Understanding the fix

```
Spring Boot 3.x standard property for OTLP tracing:
  management.otlp.tracing.endpoint=http://jaeger:4318/v1/traces

When this property is set AND micrometer-tracing-bridge-otel is on classpath:
  Spring Boot AUTOMATICALLY creates OtlpHttpSpanExporter bean
  Spring Boot AUTOMATICALLY wires it to the tracing pipeline
  You do NOT need any TracingConfig.java at all

What was wrong:
  4 services used: ${mediq.tracing.endpoint}           ← custom, non-standard
  2 services used: ${management.otlp.tracing.endpoint} ← standard

What was doubly wrong:
  ALL services also had a manual TracingConfig.java
  creating OtlpHttpSpanExporter manually
  → Spring Boot autoconfigures one + manual = TWO beans of same type
  → Spring Boot throws BeanDefinitionOverrideException
  → Or one silently overrides the other

Fix:
  1. Delete TracingConfig.java from ALL services
  2. Standardise all application.properties to use the Spring Boot key
  3. Spring Boot autoconfiguration handles everything cleanly
```

### Delete TracingConfig.java from all services

```powershell
# Run from D:\codebase\krakend_explore
Remove-Item user-service\src\main\java\com\mediq\config\TracingConfig.java
Remove-Item doctor-service\src\main\java\com\mediq\doctor\config\TracingConfig.java
Remove-Item appointment-service\src\main\java\com\mediq\appointment\config\TracingConfig.java
Remove-Item notification-service\src\main\java\com\mediq\notification\config\TracingConfig.java
Remove-Item analytics-service\src\main\java\com\mediq\analytics\config\TracingConfig.java
Remove-Item emr-service\src\main\java\com\mediq\emr\config\TracingConfig.java
```

Verify they are gone:
```powershell
Get-ChildItem -Recurse -Filter "TracingConfig.java"
# Expected: no output (all deleted)
```

---

## Fix 3 — Standardise application.properties across all services

For EACH service below, find the tracing section and replace it with the standardised version shown.

### user-service
**File:** `user-service/src/main/resources/application.properties`

Find:
```properties
# Distributed Tracing
management.tracing.sampling.probability=1.0
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

Replace with:
```properties
# ── Distributed Tracing ───────────────────────────────────────────────────────
# Spring Boot standard OTLP property — autoconfigures OtlpHttpSpanExporter
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
# Sample 100% of requests in dev (use 0.1 = 10% in production)
management.tracing.sampling.probability=1.0
# Propagation format — W3C TraceContext is the standard (used by KrakenD too)
management.tracing.propagation.type=W3C
# Include trace ID and span ID in every log line
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### doctor-service
**File:** `doctor-service/src/main/resources/application.properties`

Find:
```properties
management.tracing.sampling.probability=1.0
# Tracing
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

Replace with:
```properties
# ── Distributed Tracing ───────────────────────────────────────────────────────
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=W3C
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### appointment-service
**File:** `appointment-service/src/main/resources/application.properties`

Find:
```properties
management.tracing.sampling.probability=1.0
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

Replace with:
```properties
# ── Distributed Tracing ───────────────────────────────────────────────────────
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=W3C
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### notification-service
**File:** `notification-service/src/main/resources/application.properties`

Find:
```properties
management.tracing.sampling.probability=1.0
# Tracing
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

Replace with:
```properties
# ── Distributed Tracing ───────────────────────────────────────────────────────
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=W3C
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### analytics-service
**File:** `analytics-service/src/main/resources/application.properties`

Find:
```properties
# Tracing
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

Replace with:
```properties
# ── Distributed Tracing ───────────────────────────────────────────────────────
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=W3C
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### emr-service
**File:** `emr-service/src/main/resources/application.properties`

Find:
```properties
# Tracing
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

Replace with:
```properties
# ── Distributed Tracing ───────────────────────────────────────────────────────
management.otlp.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=W3C
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

---

## Fix 4 — Verify docker-compose Jaeger has gRPC port

**File:** `docker-compose.yml`

Find the Jaeger service and verify it exposes port 4317 (gRPC):

```yaml
  jaeger:
    image: jaegertracing/all-in-one:1.57
    container_name: mediq-jaeger
    ports:
      - "16686:16686"   # Jaeger UI
      - "4317:4317"     # OTLP gRPC  ← KrakenD uses this (use_grpc: true)
      - "4318:4318"     # OTLP HTTP  ← Spring Boot services use this
    environment:
      COLLECTOR_OTLP_ENABLED: "true"
    networks:
      - mediq-net
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
             "http://localhost:16686/"]
      interval: 10s
      timeout: 5s
      retries: 5
```

This should already be correct — just verify 4317 is present.

---

## Verification

### 1. Build all services
```powershell
cd D:\codebase\krakend_explore

# Build each service (TracingConfig removed — should still compile cleanly)
cd user-service && mvn clean package -DskipTests && cd ..
cd doctor-service && mvn clean package -DskipTests && cd ..
cd appointment-service && mvn clean package -DskipTests && cd ..
cd notification-service && mvn clean package -DskipTests && cd ..
cd analytics-service && mvn clean package -DskipTests && cd ..
cd emr-service && mvn clean package -DskipTests && cd ..

# Expected: all BUILD SUCCESS
# If any fail with "BeanDefinitionOverrideException" → TracingConfig.java
# not fully deleted — check again
```

### 2. Start all services
```powershell
docker compose up --build
```

### 3. Verify KrakenD started with telemetry
```powershell
docker logs mediq-krakend | grep -i "telemetry\|otel\|jaeger\|opentelemetry"
# Expected: log lines mentioning OpenTelemetry initialisation
```

### 4. Generate a trace
```powershell
# Register a patient through KrakenD (this creates a full trace)
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Trace",
    "lastName": "Test",
    "dateOfBirth": "1990-01-01",
    "password": "Test@1234",
    "contacts": [
      {"contactType": "EMAIL", "contactValue": "trace@mediq.com", "isPrimary": true}
    ]
  }'
```

### 5. View connected trace in Jaeger UI
```
Open browser: http://localhost:16686

Service: mediq-krakend
Operation: All
Click "Find Traces"

Expected — ONE trace with connected spans:
┌─────────────────────────────────────────────────────────────┐
│ Trace: POST /api/v1/users/patients/register     245ms total  │
│                                                             │
│ ▼ mediq-krakend                           0ms ──────────── 245ms │
│   ▼ user-service: POST /users/patients/r  5ms ─────────── 240ms │
│     ▼ DB: insert users                    6ms ──── 35ms          │
│     ▼ Redis: SET user:v1:xxx             36ms ── 40ms            │
│     ▼ Kafka: send mediq.user.events      41ms ──────────── 238ms │
└─────────────────────────────────────────────────────────────┘

If you STILL see disconnected traces (KrakenD span separate):
  → Propagation format mismatch
  → Check KrakenD version supports telemetry/opentelemetry
  → Fallback: use B3 format (see note below)
```

### 6. Verify trace ID in service logs
```powershell
docker logs mediq-user-service | grep "traceId" | tail -5
# Expected:
#  INFO [user-service,4f3a1b9c8d2e,1a2b3c4d] ... registered patient

docker logs mediq-krakend | grep "traceId\|trace_id" | tail -5
# Expected: KrakenD logs showing same traceId as user-service
```

### 7. Check no BeanDefinitionOverrideException at startup
```powershell
docker logs mediq-user-service | grep "BeanDefinition\|Error\|Exception" | head -10
# Expected: no BeanDefinitionOverrideException
```

---

## Fallback — If Traces Still Disconnected After Fix

If KrakenD and service traces still appear as separate in Jaeger, it means a **trace propagation format mismatch**. KrakenD may default to B3 format instead of W3C.

Apply this additional fix:

**In `krakend/krakend.tmpl`** — in the `telemetry/opentelemetry` section, add propagation format:
```json
"telemetry/opentelemetry": {
  "service_name": "mediq-krakend",
  ...
  "propagation": {
    "baggage": false,
    "b3": false,        ← explicitly disable B3
    "w3c": true         ← force W3C TraceContext
  }
}
```

**In all `application.properties`** — already set to W3C:
```properties
management.tracing.propagation.type=W3C
```

This ensures KrakenD and all services use the same trace header format (`traceparent` header instead of `X-B3-TraceId`).

---

## Summary of What Each Fix Does

```
Fix 1 — KrakenD telemetry config:
  Before: KrakenD generates NO spans
  After:  KrakenD generates spans per request + sends to Jaeger via gRPC
          These spans have parent context → services inherit Trace ID
          Jaeger shows ONE connected trace: KrakenD → service → DB → Kafka

Fix 2+3 — Remove TracingConfig.java + standardise property key:
  Before: Manual bean conflicts with Spring Boot autoconfiguration
          Inconsistent property keys across services
  After:  Spring Boot autoconfigures OtlpHttpSpanExporter for all services
          All services use management.otlp.tracing.endpoint
          Clean startup, no BeanDefinitionOverrideException

Fix 4 — krakend.tmpl name:
  Before: "TrueCare API Gateway"
  After:  "mediq API Gateway"
```

---

## Commit
```powershell
git add .
git commit -m "fix: distributed tracing — KrakenD telemetry + service cleanup

- Add telemetry/opentelemetry to krakend.tmpl (Bug 1 — critical)
  KrakenD now generates spans and sends to Jaeger via OTLP gRPC
  End-to-end waterfall trace now visible in Jaeger UI

- Remove TracingConfig.java from all 6 services (Bug 3 — medium)
  Spring Boot 3.x autoconfigures OtlpHttpSpanExporter automatically
  Manual bean was conflicting with autoconfiguration

- Standardise tracing property key across all services (Bug 2 — critical)
  All services now use management.otlp.tracing.endpoint
  Added management.tracing.propagation.type=W3C to ensure
  KrakenD and services use same trace header format

- Fix krakend.tmpl name from TrueCare to mediq (Bug 4 — minor)"
```
