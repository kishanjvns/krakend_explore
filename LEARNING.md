# TrueCare API Gateway — Learning Journal & Interview Prep

Everything I built and learned across Java 17, Spring Boot 3, Docker, KrakenD,
OAuth2/JWT, resilience patterns, and production deployment.
Kubernetes and Helm are marked as TODO — to be covered in the next phase.

---

## Table of Contents

1. [What I Built](#1-what-i-built)
2. [Java 17](#2-java-17)
3. [Spring Boot 3](#3-spring-boot-3)
4. [Microservices Architecture](#4-microservices-architecture)
5. [Docker & Containerisation](#5-docker--containerisation)
6. [KrakenD API Gateway](#6-krakend-api-gateway)
7. [OAuth2 / JWT / Keycloak](#7-oauth2--jwt--keycloak)
8. [Resilience Patterns](#8-resilience-patterns)
9. [Troubleshooting Lessons](#9-troubleshooting-lessons)
10. [Production Deployment](#10-production-deployment)
11. [TODO — Kubernetes & Helm](#11-todo--kubernetes--helm)
12. [Interview Q&A](#12-interview-qa)

---

## 1. What I Built

A production-pattern cloud-native API gateway for a healthcare platform (TrueCare):

- **KrakenD** — API Gateway handling routing, auth, aggregation, rate limiting, circuit breaking
- **patient-service** — Spring Boot 3 / Java 17 microservice, port 8081
- **referral-service** — Spring Boot 3 / Java 17 microservice, port 8082
- **Keycloak** — OAuth2/OIDC identity provider (local replacement for AWS Cognito), port 8090
- **Docker Compose** — full local orchestration with health-checked dependency chain

### Architecture

```
Client (Postman / Frontend)
        │  Bearer JWT
        ▼
KrakenD :8080
  ├── Validates JWT signature (RS256, Keycloak JWKS)
  ├── Checks role claims per endpoint
  ├── Strips raw token, injects X-User-* headers
  ├── Rate limiting (router + proxy level)
  ├── Circuit breaker (per backend)
  ├──▶ patient-service :8081  (Spring Boot, Java 17)
  └──▶ referral-service :8082 (Spring Boot, Java 17)

Keycloak :8090
  └── Issues JWTs, exposes JWKS for KrakenD signature verification
```

### Endpoints implemented

| Endpoint | Auth | Roles | Backends |
|----------|------|-------|----------|
| `GET /api/v1/patient-summary/{id}` | Yes | DOCTOR, ADMIN | patient-service + referral-service (aggregated) |
| `GET /api/v1/patients` | No | — | patient-service |
| `GET /api/v1/patients/{id}` | Yes | DOCTOR, NURSE, ADMIN | patient-service |
| `GET /api/v1/patients/status/{status}` | No | — | patient-service |
| `GET /api/v1/patients/active` | No | — | patient-service |
| `GET /api/v1/referrals` | Yes | DOCTOR, ADMIN | referral-service |
| `GET /api/v1/referrals/open` | Yes | DOCTOR, NURSE, ADMIN | referral-service |
| `GET /api/v1/referrals/patient/{patientId}` | Yes | DOCTOR, NURSE, ADMIN | referral-service |
| `GET /api/v1/referrals/{referralId}` | Yes | DOCTOR, NURSE, ADMIN | referral-service |

---

## 2. Java 17

### Features used and why

| Feature | Where used | Why it matters |
|---------|-----------|----------------|
| **Records** | `Patient`, `Referral`, `UserContext`, `PatientResponse` | Immutable data carriers — auto-generates constructor, getters, equals, hashCode, toString |
| **Sealed interfaces** | `PatientStatus`, `ReferralStatus` | Restricts subtypes to known set — enables exhaustive switch, compiler catches missing cases |
| **Switch expressions** | `PatientStatus.from()` | Returns values directly, no fall-through, arrow syntax |
| **Compact constructors** | `Patient`, `UserContext` | Validates record fields before construction completes |
| **Stream.toList()** | Service layer | Shorthand for unmodifiable list — safer than `Collectors.toList()` |
| **List.of()** | In-memory data store | Truly unmodifiable — throws on mutation attempt |
| **Method references** | `PatientResponse::from` | Cleaner than lambdas for mapping |
| **ThreadLocal** | `UserContextHolder` | Per-request JWT claim storage scoped to Tomcat thread |

### Records vs traditional classes

```java
// Java 17 record — 1 line replaces ~50 lines of boilerplate
public record UserContext(String userId, String email, String role, String name) {
    // compact constructor for validation
    public UserContext {
        Objects.requireNonNull(userId, "userId required");
    }
    public boolean isDoctor() { return "DOCTOR".equals(role); }
}
```

### Sealed interfaces + switch expressions

```java
public sealed interface PatientStatus permits Admitted, Outpatient, Critical, Discharged {
    record Admitted()    implements PatientStatus {}
    record Outpatient()  implements PatientStatus {}
    record Critical()    implements PatientStatus {}
    record Discharged()  implements PatientStatus {}

    static PatientStatus from(String value) {
        return switch (value.toLowerCase()) {
            case "admitted"   -> new Admitted();
            case "outpatient" -> new Outpatient();
            case "critical"   -> new Critical();
            case "discharged" -> new Discharged();
            default -> throw new IllegalArgumentException("Unknown status: " + value);
        };
    }
}
```

**Why sealed:** compiler enforces exhaustive switch — if you add a new status and forget to handle it, it's a compile error, not a runtime bug.

### ThreadLocal — critical interview topic

```java
public class UserContextHolder {
    private static final ThreadLocal<UserContext> holder = new ThreadLocal<>();

    public static void set(UserContext ctx) { holder.set(ctx); }
    public static UserContext get()         { return holder.get(); }
    public static void clear()              { holder.remove(); }  // NOT set(null)
}
```

**Why `.remove()` not `.set(null)`:** Tomcat reuses threads across requests. `set(null)` leaves the ThreadLocal entry in the map — next request on the same thread could see stale data from the previous request. `.remove()` cleans the entry entirely.

**Where to clear:** always in `afterCompletion()` of the HandlerInterceptor, never in `postHandle()`. `afterCompletion` runs even when exceptions are thrown — `postHandle` does not.

---

## 3. Spring Boot 3

### @SpringBootApplication is composed

```java
@SpringBootApplication
// equivalent to:
@SpringBootConfiguration      // marks as config source
@EnableAutoConfiguration      // activates auto-config based on classpath
@ComponentScan                // scans current package and sub-packages
```

### Auto-configuration strategy

Spring Boot configures beans automatically unless you override them. `@ConditionalOnMissingBean` means your custom bean takes precedence over the auto-configured one.

```java
@Configuration
public class JacksonConfig {
    @Bean  // overrides Spring Boot's default ObjectMapper
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

### Constructor injection over field injection

```java
// Field injection — avoid (hides dependencies, hard to test, not immutable)
@Autowired private PatientService service;

// Constructor injection — correct (immutable, fail-fast on startup, easy to test)
private final PatientService service;
public PatientController(PatientService service) {
    this.service = service;
}
```

### HandlerInterceptor for JWT claim propagation

KrakenD strips the raw JWT and forwards decoded claims as headers. The interceptor extracts them into a ThreadLocal so any controller can access caller identity without touching the headers directly.

```
Request arrives
    ↓
JwtClaimsInterceptor.preHandle()
  → reads X-User-Id, X-User-Email, X-User-Role, X-User-Name
  → creates UserContext record
  → stores in UserContextHolder (ThreadLocal)
    ↓
Controller.method()
  → UserContextHolder.get() → audit log / role check / business logic
    ↓
JwtClaimsInterceptor.afterCompletion()
  → UserContextHolder.clear()   ← always runs, even on exception
```

### WebMvcConfigurer — extend without replacing

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtClaimsInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**");
    }
}
```

Implements `WebMvcConfigurer` rather than extending `WebMvcConfigurationSupport` — the latter replaces all Spring MVC auto-configuration.

### Why Spring Security is disabled

JWT validation is centralised at KrakenD. Services are ClusterIP — unreachable from outside the cluster. They only receive requests from KrakenD which has already validated the token and stripped it. Re-validating at service level is redundant and adds latency.

```properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### Global exception handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PatientNotFoundException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse(404, "Not Found", ex.getMessage()));
    }
}
```

---

## 4. Microservices Architecture

### Core principles implemented

| Principle | How implemented |
|-----------|----------------|
| Single responsibility | patient-service owns patient domain, referral-service owns referral domain |
| Independent deployability | Each has its own `Dockerfile`, `pom.xml`, port |
| API Gateway pattern | KrakenD is the single entry point — handles auth, routing, aggregation |
| Internal vs external API | `/api/v1/patients/{id}` (external) maps to `/patients/{id}` (internal) |
| Domain model vs DTO | `Patient` record (internal) → `PatientResponse` DTO (API response) |
| Stateless design | No session state — required for horizontal scaling / HPA |
| Health checks | `/actuator/health` on each service — required for Docker/K8s orchestration |

### Service discovery — local vs production

```
Local (Docker Compose):
  patient-service:8081   ← Docker bridge network DNS
  referral-service:8082

Production (Kubernetes):
  patient-service.default.svc.cluster.local:8081   ← K8s ClusterIP DNS
  referral-service.default.svc.cluster.local:8082
```

Both use hostname-based service discovery — the only difference is who manages the DNS.

---

## 5. Docker & Containerisation

### Multi-stage Dockerfile

```dockerfile
# Stage 1 — build (Maven + JDK 17, ~350MB)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q        # cache layer — skipped if pom.xml unchanged
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2 — runtime (JRE-only Alpine, ~80MB)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/patient-service-1.0.0.jar patient-service.jar
ENTRYPOINT ["java",
  "-XX:+UseContainerSupport",       # JVM reads cgroup limits, not host RAM
  "-XX:MaxRAMPercentage=75.0",      # 75% of container memory limit for heap
  "-jar", "patient-service.jar"]
```

**Why two stages:** build stage produces fat JAR using full JDK. Runtime stage uses only JRE (no compiler, no Maven). Final image is ~80MB vs ~350MB. Smaller image = faster pull, smaller attack surface.

### Docker Compose health dependency chain

```
Keycloak :8090
    ↓ condition: service_healthy
patient-service :8081 ──┐
referral-service :8082 ─┤  condition: service_healthy (both)
                         ↓
                    KrakenD :8080
```

KrakenD starts only after all three dependencies are healthy. Without this, KrakenD starts, tries to fetch JWKS from Keycloak, fails, and the JWK cache is never populated.

### Keycloak health check — what failed and why

**Original check (broken):**
```yaml
test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/8090 && echo -e 'GET /health/ready HTTP/1.1...'"]
```

Three compounding failures:
1. `/health/ready` requires `--health-enabled=true` flag — not enabled by default → 404
2. `HTTP/1.1` keep-alive — `cat <&3` blocks forever waiting for connection close → timeout
3. `CMD-SHELL` uses `/bin/sh` — `/dev/tcp` is bash-only → silent failure on non-bash shells

**Working fix:**
```yaml
test: ["CMD", "/bin/bash", "-c",
  "exec 3<>/dev/tcp/localhost/8090 &&
   printf 'GET /realms/trucare HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 &&
   timeout 3 cat <&3 | head -1 | grep -q '200'"]
```

| Change | Reason |
|--------|--------|
| `/bin/bash` explicitly via `CMD` | `/dev/tcp` is bash-only, guaranteed on UBI9 |
| `/realms/trucare` not `/health/ready` | Always available, also proves realm imported |
| `HTTP/1.0` | Server closes connection after response — `cat` exits cleanly |
| `timeout 3 cat` | Safety net against any hang |
| `head -1 \| grep -q '200'` | Checks only the status line |

---

## 6. KrakenD API Gateway

### Core concepts

**Endpoint → Backend mapping:**
```
External (client-facing)          Internal (service-facing)
/api/v1/patients/{id}     ──▶    http://patient-service:8081/patients/{id}
/api/v1/patient-summary/{id} ──▶ patient-service /patients/{id}
                              +   referral-service /referrals/patient/{id}
                                  (merged into one JSON response)
```

**Key encoding modes:**

| Mode | What it does | Use when |
|------|-------------|----------|
| `no-op` | Passes response through unchanged | Single backend, no transformation needed |
| `json` | Parses and merges JSON from backends | Aggregating multiple backends |

**Response shaping:**
- `allow` — field whitelist (strips fields not in the list)
- `mapping` — renames keys (`"collection"` → `"referrals"`)
- `is_collection` — tells KrakenD backend returns a JSON array

### JWT validation flow

```
1. Client: Authorization: Bearer <JWT>
2. KrakenD fetches public key from Keycloak JWKS (cached 900s)
3. KrakenD verifies JWT signature (RS256)
4. KrakenD checks role claim matches endpoint's allowed roles
5. KrakenD strips raw token, injects claims as headers:
      X-User-Id    ← JWT sub
      X-User-Email ← JWT email
      X-User-Role  ← JWT role
      X-User-Name  ← JWT name
6. Backend receives headers — never sees raw JWT
```

**`disable_jwk_security: true`**
Allows KrakenD to fetch JWKS over HTTP (not HTTPS). Does NOT skip authentication. Only controls the transport protocol for the key-fetching step. Required locally (HTTP). Remove in production (HTTPS).

**Common 401 causes (checklist):**
1. Token expired — get a fresh token
2. Wrong role — decode at jwt.io, check `role` claim
3. `disable_jwk_security` missing — KrakenD can't fetch public key over HTTP
4. `issuer` mismatch — must exactly match `iss` in token (including port number)
5. `roles_key` wrong — field name in JWT (`"role"` not `"roles"`)

### Flexible Configuration (template system)

**Structure:**
```
krakend/
├── krakend.tmpl                     ← root template, ~20 lines
├── settings/
│   └── hosts.json                   ← all service URLs in one place
└── partials/
    ├── auth_doctor_admin.tmpl        ← roles: [DOCTOR, ADMIN]
    ├── auth_doctor_nurse_admin.tmpl  ← roles: [DOCTOR, NURSE, ADMIN]
    ├── circuit_breaker.tmpl          ← defined once, used on every backend
    ├── rate_limit_proxy.tmpl         ← defined once, used on every backend
    ├── endpoint_patients.tmpl        ← all patient-domain endpoints
    └── endpoint_referrals.tmpl       ← all referral-domain endpoints
```

**Why templates:** the auth block is ~14 lines. Without templates it was copy-pasted across 7 endpoints. Change the jwk_url → edit 7 files. With templates → edit 1 file.

**Auth template rule:**
- Same role combo on 2+ endpoints → create a template file
- Unique role combo on 1 endpoint → inline it directly

**Adding a new endpoint:**
1. Add the block to the relevant domain template (`endpoint_patients.tmpl`)
2. Reference the appropriate auth template (`{{ template "auth_doctor_admin.tmpl" . }}`)
3. Run `krakend check --lint` to validate
4. Done — no other files need touching

**Validate templates locally:**
```bash
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$PWD/krakend:/etc/krakend" \
  -e FC_ENABLE=1 \
  -e FC_TEMPLATES=/etc/krakend/partials \
  -e FC_SETTINGS=/etc/krakend/settings \
  devopsfaith/krakend:2.7 \
  check --config /etc/krakend/krakend.tmpl
```

---

## 7. OAuth2 / JWT / Keycloak

### Token flow (Password grant — for local testing)

```bash
curl -X POST http://localhost:8090/realms/trucare/protocol/openid-connect/token \
  -d "client_id=trucare-gateway" \
  -d "client_secret=trucare-secret-local" \
  -d "username=dr.mehta&password=doctor123&grant_type=password"
# returns: { "access_token": "<JWT>", "expires_in": 3600, ... }
```

### JWT structure

```
eyJhbGci...  .  eyJzdWIi...  .  AIfYzyt...
    │                │               │
  Header           Payload        Signature
  alg: RS256       sub, email,    RS256 signed with
  kid: key-id      role, name,    Keycloak private key
                   iss, exp, iat  (verify with public key
                                   from JWKS endpoint)
```

**Important:** JWT payload is Base64-encoded, NOT encrypted. Anyone can decode it. The signature only proves it was issued by Keycloak — it does not hide the data.

### RS256 vs HS256

| | RS256 (used here) | HS256 |
|-|------------------|-------|
| Algorithm | RSA asymmetric | HMAC symmetric |
| Sign with | Private key (Keycloak keeps it) | Shared secret |
| Verify with | Public key (anyone can have it) | Same shared secret |
| Security | Private key never leaves Keycloak | Secret must be shared with every verifier |
| Use when | Distributed systems (multiple verifiers) | Single service or tight trust boundary |

### Keycloak realm structure

```
Realm: trucare
  ├── Roles: DOCTOR, NURSE, ADMIN, PATIENT
  ├── Client: trucare-gateway
  │     ├── client_secret: trucare-secret-local
  │     └── Protocol mapper: realm-roles-mapper
  │           → injects realm role into JWT as "role" claim
  └── Users:
        dr.mehta    / doctor123  → DOCTOR
        nurse.priya / nurse123   → NURSE
        admin       / admin123   → ADMIN
```

**Issuer mismatch — common local dev issue:**
- Token `iss` claim contains: `http://localhost:8090/realms/trucare`
- KrakenD calls Keycloak internally via: `http://keycloak:8090`
- `issuer` in krakend.json must match the token exactly → `http://localhost:8090/realms/trucare`
- `jwk_url` uses internal hostname → `http://keycloak:8090/realms/trucare/...`
- These are different fields serving different purposes

---

## 8. Resilience Patterns

### Circuit Breaker

**Three states:**
```
         errors >= max_errors
Closed ─────────────────────▶ Open
(normal traffic)              (fast-fail, no backend calls)
   ▲                               │
   │        timeout expires        │
   └─── Half-Open ◀────────────────┘
        (1 probe request)
        probe succeeds → Closed
        probe fails    → Open again
```

**Config:**
```json
"qos/circuit-breaker": {
  "interval":          60,    // error counting window in seconds
  "timeout":           10,    // open → half-open wait in seconds
  "max_errors":         5,    // errors in window before opening
  "log_status_change": true   // observability only — no behaviour effect
}
```

**Observed behaviour (live tested — referral-service stopped):**

| Request | Response time | Circuit state | What KrakenD did |
|---------|--------------|---------------|-----------------|
| 1–5 | ~2.5s | Closed | Waited for connection timeout on dead backend |
| 6 | ~2.5s | Closed → **Open** | 5th error crossed threshold, circuit tripped |
| 7–8 | ~0.03s | Open | Fast-fail — no network call at all |
| After restore | ~0.47s | Half-Open → **Closed** | Probe succeeded, normal operation resumed |

**KrakenD log output:**
```
ERROR   context deadline exceeded          ← requests 1-5
WARNING [CB] went from 'closed' to 'open'  ← threshold crossed
ERROR   circuit breaker is open            ← fast-fail on 7-8
WARNING [CB] went from 'open' to 'half-open'   ← timeout expired
WARNING [CB] went from 'half-open' to 'closed' ← probe succeeded
```

**Why circuit breaker matters:** without it, every request to a dead backend waits the full 2500ms timeout. With 100 concurrent users, that's 250 threads blocked — thread exhaustion, cascading failure. With circuit open, same 100 requests complete in 30ms total.

### Partial failure behaviour (live tested)

| Scenario | HTTP Status | X-KrakenD-Completed | Body |
|----------|-------------|---------------------|------|
| Both backends healthy | 200 | `true` | Full JSON including referrals |
| One backend down | 200 | `false` | Partial — referrals key silently absent |
| Both backends down | 500 | `false` | Error message |

**Critical:** KrakenD returns HTTP 200 with partial data when at least one backend succeeds. The only signal of incompleteness is `X-KrakenD-Completed: false`.

**Frontend rule:** always check `X-KrakenD-Completed` before rendering data-dependent sections. Do not rely on HTTP status alone for aggregated endpoints.

**CDN risk:** HTTP 200 partial responses get cached by CDNs. A CDN serves stale partial data to all subsequent users. Fix: return non-2xx on partial failure so CDN never caches it.

### Hard vs soft dependency

| Type | Behaviour on failure | Use when |
|------|---------------------|----------|
| **Hard** | Fail entire request (5xx) | Data is essential — partial is worse than nothing |
| **Soft** | Return cached data or partial response | Enhancement data — page still works without it |

In TrueCare:
- Patient demographics = **hard** (pointless to show empty patient card)
- Referrals on summary = **hard** (clinical decisions need complete picture)
- Recommendations (TODO) = **soft** (nice to have, page works without them)

### Rate limiting

Two levels configured on `patient-summary`:

| Level | Config key | Controls |
|-------|-----------|---------|
| Router (endpoint) | `qos/ratelimit/router` | Total requests/sec to the endpoint |
| Proxy (backend) | `qos/ratelimit/proxy` with `key: "{{.JWT.sub}}"` | Requests/sec per individual user (JWT sub claim) |

Router limit protects the gateway. Proxy limit with JWT sub prevents a single user from hammering a backend.

---

## 9. Troubleshooting Lessons

### Lesson 1 — Keycloak health check always failing

**Symptom:** `dependency failed to start: container keycloak is unhealthy`

**Root causes (three compounding):**
1. `/health/ready` not exposed without `--health-enabled=true`
2. `HTTP/1.1` keep-alive causes `cat` to block
3. `curl`/`wget` not in UBI9-minimal — `CMD-SHELL` can't find them

**Fix:** Use bash `/dev/tcp` with `HTTP/1.0` hitting `/realms/trucare`

---

### Lesson 2 — patient-summary returns 401, other endpoints work

**Symptom:** Same valid JWT works on `/api/v1/patients/{id}` but fails on `/api/v1/patient-summary/{id}`

**Root cause:** `disable_jwk_security: true` was missing from the `patient-summary` auth block.

Without it, KrakenD refuses to fetch the JWKS over HTTP → cannot get public key → cannot verify any token → every request returns 401 regardless of token validity.

**Fix:** Add `disable_jwk_security: true` and `cache_duration: 900` to the missing endpoint.

---

### Lesson 3 — error_body config silently ignored

**Symptom:** Added `error_body` to router config, custom error messages not appearing.

**Root cause:** `error_body` is a KrakenD Enterprise feature. CE (Community Edition) silently ignores it.

**Fix:** Use `return_error_msg: true` in CE — surfaces KrakenD's internal error text in the response body.

---

### Lesson 4 — Windows path rewriting breaks Docker volume mounts in bash

**Symptom:** `docker run -v "D:/path:/etc/krakend"` → path gets rewritten to `C:/Program Files/Git/...`

**Fix:** Prefix the command with `MSYS_NO_PATHCONV=1` to disable Git Bash path conversion.

---

## 10. Production Deployment

### Local → Production evolution

| Concern | Local (Docker Compose) | Production (EKS) |
|---------|----------------------|-----------------|
| Config | Bind mount `./krakend` | Baked into custom Docker image |
| Service URLs | `settings/hosts.json` | Kubernetes ConfigMap (mounted at same path) |
| KrakenD image | `devopsfaith/krakend:2.7` (official) | Custom image pushed to ECR |
| Secrets | docker-compose env vars | AWS Secrets Manager / K8s Secrets |
| Scaling | Single container | HPA — Horizontal Pod Autoscaler |
| TLS | None | ACM cert on ALB / ingress |

### Custom KrakenD image (production Dockerfile)

```dockerfile
# Stage 1 — compile templates → krakend.json
FROM devopsfaith/krakend:2.7 AS builder
COPY krakend/ /etc/krakend/
ENV FC_ENABLE=1 \
    FC_TEMPLATES=/etc/krakend/partials \
    FC_SETTINGS=/etc/krakend/settings
RUN krakend check \
      --config /etc/krakend/krakend.tmpl \
      --output /etc/krakend/krakend.json

# Stage 2 — runtime image with compiled JSON only
FROM devopsfaith/krakend:2.7
COPY --from=builder /etc/krakend/krakend.json /etc/krakend/krakend.json
CMD ["run", "--config", "/etc/krakend/krakend.json"]
```

Template files (.tmpl, partials/, settings/) are only needed at build time.
Runtime image contains only the binary and compiled JSON — clean, minimal, auditable.

### ConfigMap replaces hosts.json in Kubernetes

The image stays identical across all environments. Only the ConfigMap changes.

```yaml
# Helm values.prod.yaml
krakend:
  hosts:
    patient_service:   "http://patient-service.prod.svc.cluster.local:8081"
    referral_service:  "http://referral-service.prod.svc.cluster.local:8082"
    keycloak_internal: "http://keycloak.prod.svc.cluster.local:8090"
    keycloak_external: "https://auth.trucare.com"
```

ConfigMap mounts the file at `/etc/krakend/settings/hosts.json` — same path KrakenD reads locally. KrakenD has no idea whether the file came from a bind mount or ConfigMap.

### CI/CD pipeline

```
Stage 1 — Validate
  krakend check --config krakend.tmpl --lint
  Exit 1 → pipeline fails, image never built

Stage 2 — Integration tests
  Spin up stack, run auth/role assertions:
  NURSE token  → /api/v1/referrals     → assert 403
  DOCTOR token → /api/v1/referrals     → assert 200
  No token     → /api/v1/patients/{id} → assert 401

Stage 3 — Build & push to ECR
  docker build -t <ecr-url>/krakend:v1.4.2 .
  docker push <ecr-url>/krakend:v1.4.2

Stage 4 — Deploy to EKS
  helm upgrade --install krakend ./helm/krakend \
    --set image.tag=v1.4.2 \
    --values values.prod.yaml

Stage 5 — Manual approval gate (prod only)
```

### What krakend check catches vs misses

| Error type | Caught by check? |
|------------|-----------------|
| JSON syntax error | Yes |
| Unknown KrakenD field / typo | Yes |
| Missing template reference | Yes |
| Wrong field type | Yes |
| Wrong roles on an endpoint | **No** — needs integration test |
| Typo in backend hostname | **No** — needs integration test |

### Multi-team config ownership

```
Gateway Config Repo
├── krakend.tmpl                    ← platform team
├── settings/hosts.json             ← per-environment (ConfigMap in K8s)
└── partials/
    ├── endpoint_patients.tmpl      ← patient-service team owns
    ├── endpoint_referrals.tmpl     ← referral-service team owns
    ├── auth_*.tmpl                 ← platform team owns
    ├── circuit_breaker.tmpl        ← platform team owns
    └── rate_limit_proxy.tmpl       ← platform team owns
```

Adding a new endpoint = PR to gateway repo adding one block to your team's `.tmpl` file.

---

## 11. TODO — Kubernetes & Helm

- [ ] Write Helm chart for KrakenD (`helm/krakend/`)
- [ ] Write Helm charts for patient-service and referral-service
- [ ] `values.dev.yaml`, `values.staging.yaml`, `values.prod.yaml`
- [ ] ConfigMap for `hosts.json` injected via Helm values
- [ ] Deployment manifest with readiness/liveness probes
- [ ] HPA (Horizontal Pod Autoscaler) for KrakenD
- [ ] Ingress with TLS termination (ACM on ALB)
- [ ] Deploy to local Kubernetes (minikube / kind) first
- [ ] Then deploy to EKS using CI/CD pipeline above
- [ ] K8s service discovery: `<service>.<namespace>.svc.cluster.local`
- [ ] Secrets management: K8s Secrets / AWS Secrets Manager

---

## 12. Interview Q&A

**Q: Why use an API Gateway instead of having clients call services directly?**
Single entry point for auth, rate limiting, SSL termination, routing, and aggregation. Without it, every client needs to know every service URL, handle auth per service, and make multiple calls for aggregated data. Backend services stay simple — they don't implement auth or aggregation logic.

**Q: How does KrakenD differ from Nginx?**
KrakenD understands API semantics — it merges responses from multiple backends, validates JWTs, transforms JSON, and applies per-endpoint policies. Nginx is a reverse proxy that routes HTTP traffic. KrakenD is an API composition layer.

**Q: Why is Spring Security disabled in the microservices?**
JWT validation is centralised at KrakenD. Services are ClusterIP — unreachable from outside the cluster. They trust `X-User-*` headers injected by the gateway. Re-validating at service level is redundant and adds latency. This is the standard API Gateway auth pattern.

**Q: Explain the circuit breaker pattern.**
Three states: Closed (normal), Open (fast-fail), Half-Open (recovery probe). After `max_errors` failures in the interval window, circuit opens — KrakenD immediately rejects requests without calling the backend. After the timeout, one probe request is allowed. Success → Closed, Failure → Open again. Prevents thread exhaustion and cascading failures.

**Q: What is X-KrakenD-Completed and why does it matter?**
KrakenD sets `X-KrakenD-Completed: false` when at least one backend in an aggregated endpoint failed. HTTP status is still 200. Clients must check this header before rendering data-dependent UI — a 200 on an aggregated endpoint does not guarantee complete data. Unchecked, this causes silent data loss and misleading UIs.

**Q: What does disable_jwk_security do?**
It allows KrakenD to fetch the JWKS public key over HTTP instead of HTTPS. It does NOT skip JWT validation, role checking, or any authentication logic. Required in local development where Keycloak runs over HTTP. In production with HTTPS, this flag is removed.

**Q: How do you manage KrakenD config across multiple teams?**
Flexible Configuration (Go templates). Each service team owns their endpoint template file. Platform team owns cross-cutting templates (auth, circuit breaker, rate limit). Changes go through PRs. CI validates with `krakend check --lint` before any image is built.

**Q: How do you deploy config changes without touching microservices?**
KrakenD config is a separate Docker image artifact. Changing endpoint config, auth rules, or rate limits only requires rebuilding and redeploying the KrakenD image. Microservices are completely untouched.

**Q: What is the difference between hard and soft dependencies in an aggregated endpoint?**
Hard dependency: if it fails, the entire request fails (return 5xx). Used when the data is essential and a partial response is misleading or dangerous. Soft dependency: if it fails, return cached data or omit the section gracefully. Used for enhancement data where the page still makes sense without it.

**Q: How does ThreadLocal work and what's the risk in a web framework?**
ThreadLocal stores a value scoped to the current thread. In a web framework like Spring/Tomcat, threads are pooled and reused across requests. If you don't call `.remove()` after the request completes, the next request on the same thread sees the previous request's data. Always clear in `afterCompletion()` of a HandlerInterceptor, which runs even when exceptions are thrown.
