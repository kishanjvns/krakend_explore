# mediq — Milestone 1 (Remaining): Probes + Distributed Tracing

## Branch
```powershell
# Branch from M1 user-service branch
git checkout feature/mediq-m1-user-service
git checkout -b feature/mediq-m1-probes-tracing
```

## What This Milestone Covers
- M1.3: Custom Liveness + Readiness probes on user-service
- M1.4: Distributed Tracing with OpenTelemetry + Jaeger
- Both added as EXTENSIONS to existing user-service — no new service

## Scope
```
Files to MODIFY:
  user-service/pom.xml                          ← add tracing dependencies
  user-service/src/main/resources/application.properties ← add tracing config
  docker-compose.yml                            ← add Jaeger container

Files to CREATE:
  user-service/src/main/java/com/mediq/health/DatabaseHealthIndicator.java
  user-service/src/main/java/com/mediq/health/KafkaHealthIndicator.java
  user-service/src/main/java/com/mediq/health/RedisHealthIndicator.java
  user-service/src/main/java/com/mediq/config/TracingConfig.java

Files to NOT TOUCH:
  referral-service/   ← leave untouched
  krakend/            ← leave untouched
  keycloak/           ← leave untouched
```

---

## Part 1 — Liveness and Readiness Probes

### Concept
```
Liveness probe:  "Is this container ALIVE or should Kubernetes RESTART it?"
                 Fails → Kubernetes kills and restarts the container
                 Use for: deadlocks, hung threads, OOM states

Readiness probe: "Is this container READY to receive traffic?"
                 Fails → Kubernetes removes pod from Service endpoints
                 Pod stays alive but gets no traffic
                 Use for: startup warmup, dependency not yet available,
                          temporary overload
```

### Step 1: Add pom.xml dependencies

Add these dependencies to `user-service/pom.xml` inside `<dependencies>`:

```xml
<!-- Micrometer — metrics for Prometheus scraping -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- OpenTelemetry tracing bridge -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OpenTelemetry OTLP exporter — sends traces to Jaeger -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Spring Boot actuator already present — verify it is there -->
<!-- spring-boot-starter-actuator -->
```

### Step 2: Custom Health Indicators

Create `user-service/src/main/java/com/mediq/health/DatabaseHealthIndicator.java`:

```java
package com.mediq.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("database")
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            // Simple connectivity check — does not scan tables
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class);
            if (result != null && result == 1) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("schema", "mediq_users")
                    .withDetail("status", "reachable")
                    .build();
            }
            return Health.down()
                .withDetail("error", "Unexpected query result")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

Create `user-service/src/main/java/com/mediq/health/RedisHealthIndicator.java`:

```java
package com.mediq.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try {
            // PING Redis — returns PONG if alive
            String pong = connectionFactory.getConnection()
                .ping();
            if ("PONG".equals(pong)) {
                return Health.up()
                    .withDetail("redis", "reachable")
                    .withDetail("response", pong)
                    .build();
            }
            return Health.down()
                .withDetail("error", "Unexpected ping response: " + pong)
                .build();
        } catch (Exception e) {
            // Redis down → service is still alive (fail-open cache)
            // Readiness: Redis down = NOT ready (traffic would miss cache)
            return Health.down()
                .withDetail("redis", "unreachable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

Create `user-service/src/main/java/com/mediq/health/KafkaHealthIndicator.java`:

```java
package com.mediq.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    private final String bootstrapServers;

    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Health health() {
        // Try to list topics — proves broker is reachable
        try (AdminClient client = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            client.listTopics()
                .names()
                .get(3, TimeUnit.SECONDS);  // 3 second timeout
            return Health.up()
                .withDetail("kafka", "reachable")
                .withDetail("brokers", bootstrapServers)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("kafka", "unreachable")
                .withDetail("brokers", bootstrapServers)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### Step 3: Update application.properties

Add these lines to `user-service/src/main/resources/application.properties`:

```properties
# ── Health probes ─────────────────────────────────────────────────────────────
# Liveness: is the app alive? (DB + basic JVM health)
management.endpoint.health.group.liveness.include=livenessState,database
management.endpoint.health.group.liveness.show-details=always

# Readiness: is the app ready for traffic? (all dependencies)
management.endpoint.health.group.readiness.include=readinessState,database,redis,kafka
management.endpoint.health.group.readiness.show-details=always

# Expose the dedicated probe endpoints
management.endpoints.web.exposure.include=health,info,metrics,prometheus,liveness,readiness
management.endpoint.health.probes.enabled=true

# ── Prometheus metrics ────────────────────────────────────────────────────────
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=user-service
management.metrics.tags.environment=${ENVIRONMENT:local}
```

### Step 4: Verify probe endpoints work

After starting the service, these endpoints must respond:

```
GET http://localhost:8081/actuator/health/liveness
→ {"status":"UP","components":{"database":{"status":"UP"},"livenessState":{"status":"UP"}}}

GET http://localhost:8081/actuator/health/readiness
→ {"status":"UP","components":{"database":{"status":"UP"},"kafka":{"status":"UP"},"readinessState":{"status":"UP"},"redis":{"status":"UP"}}}
```

---

## Part 2 — Distributed Tracing with OpenTelemetry + Jaeger

### Concept
```
Problem: request flows through KrakenD → user-service → Kafka
         One of them is slow. How do you find which one?

Solution: Trace ID
  → KrakenD assigns a unique Trace ID to every incoming request
  → Passes it as HTTP header to user-service
  → user-service propagates it to all outgoing calls
  → Each service records its own SPAN (start time, end time, name)
  → All spans with same Trace ID = one request's full journey
  → Jaeger collects all spans → shows waterfall view

Waterfall view example:
  Request abc-123:
    KrakenD             0ms → 245ms  (total gateway time)
    user-service        5ms → 240ms  (45ms processing)
      → DB query        6ms → 35ms   (29ms — fast)
      → Redis write     36ms → 40ms  (4ms — fast)
      → Kafka publish   41ms → 238ms (197ms — SLOW ← found it)
```

### Step 5: Add TracingConfig

Create `user-service/src/main/java/com/mediq/config/TracingConfig.java`:

```java
package com.mediq.config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @Value("${mediq.tracing.endpoint:http://localhost:4318/v1/traces}")
    private String tracingEndpoint;

    @Bean
    public OtlpHttpSpanExporter otlpHttpSpanExporter() {
        // Sends spans to Jaeger via OTLP protocol
        return OtlpHttpSpanExporter.builder()
            .setEndpoint(tracingEndpoint)
            .build();
    }
}
```

### Step 6: Add tracing config to application.properties

Add to `user-service/src/main/resources/application.properties`:

```properties
# ── Distributed Tracing ───────────────────────────────────────────────────────
# Sample 100% of requests in dev (reduce in production: 0.1 = 10%)
management.tracing.sampling.probability=1.0

# Service name shown in Jaeger UI
spring.application.name=user-service

# OTLP endpoint — Jaeger collector
mediq.tracing.endpoint=${JAEGER_ENDPOINT:http://localhost:4318/v1/traces}

# Include trace ID in log output
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### Step 7: Add Jaeger to docker-compose.yml

Add this service to `docker-compose.yml` — insert BEFORE the `krakend` service:

```yaml
  # ── Jaeger — distributed tracing UI ─────────────────────────────────────────
  jaeger:
    image: jaegertracing/all-in-one:1.57
    container_name: mediq-jaeger
    ports:
      - "16686:16686"   # Jaeger UI
      - "4317:4317"     # OTLP gRPC
      - "4318:4318"     # OTLP HTTP
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

Also add Jaeger environment variable to `user-service` in docker-compose.yml:

```yaml
  user-service:
    environment:
      # ... existing env vars ...
      JAEGER_ENDPOINT: http://jaeger:4318/v1/traces
```

And add Jaeger to user-service `depends_on`:

```yaml
  user-service:
    depends_on:
      # ... existing depends_on ...
      jaeger:
        condition: service_healthy
```

---

## Verification

### 1. Build
```powershell
cd D:\codebase\krakend_explore\user-service
mvn clean package -DskipTests
# Expected: BUILD SUCCESS
```

### 2. Start all services
```powershell
cd D:\codebase\krakend_explore
docker compose up --build
```

### 3. Test liveness probe
```powershell
curl http://localhost:8081/actuator/health/liveness
# Expected: {"status":"UP"}
```

### 4. Test readiness probe
```powershell
curl http://localhost:8081/actuator/health/readiness
# Expected: {"status":"UP"} with database, redis, kafka all UP
```

### 5. Generate a trace
```powershell
# Register a patient (generates a trace)
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Trace",
    "lastName": "Test",
    "dateOfBirth": "1990-01-01",
    "password": "Test@1234",
    "contacts": [{"contactType": "EMAIL", "contactValue": "trace@test.com", "isPrimary": true}]
  }'
```

### 6. View trace in Jaeger UI
```
Open browser: http://localhost:16686
Service: user-service
Operation: POST /users/patients/register
Click "Find Traces"
→ Should see one trace with spans for:
   HTTP POST handler
   DB transaction (Flyway + JPA)
   Redis write
   Kafka publish
```

### 7. Check trace ID in logs
```powershell
docker logs mediq-user-service | grep "traceId"
# Expected: log lines containing [user-service,<traceId>,<spanId>]
```

### 8. Test Prometheus metrics
```powershell
curl http://localhost:8081/actuator/prometheus
# Expected: large block of metrics including:
# http_server_requests_seconds_count
# jvm_memory_used_bytes
# hikaricp_connections_active
```

---

## Commit
```powershell
cd D:\codebase\krakend_explore
git add .
git commit -m "feat(m1): add liveness/readiness probes and distributed tracing

- Custom HealthIndicator for DB, Redis, Kafka
- Liveness endpoint: /actuator/health/liveness
- Readiness endpoint: /actuator/health/readiness
- OpenTelemetry tracing with OTLP exporter
- Jaeger added to docker-compose
- Trace ID propagated in log output
- Prometheus metrics endpoint enabled"
```

---

## What to Bring Back for Review
Zip the project and share. We will verify:
1. Liveness probe returns UP
2. Readiness probe returns UP for all 3 dependencies
3. At least one trace visible in Jaeger UI with multiple spans
4. Trace ID appears in log lines
