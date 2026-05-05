# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TrueCare API Gateway — a step-by-step tutorial project building a cloud-native API gateway using KrakenD with Spring Boot microservices and Keycloak for identity. The current branch (`dev/krakend-step4-auth`) implements JWT authentication via Keycloak.

## Development Commands

### Start all services
```bash
docker-compose up --build
```
Keycloak takes 60–90s on first start. KrakenD starts last (depends on all three services being healthy).

### Start without rebuilding Java images
```bash
docker-compose up
```

### Rebuild a single service
```bash
docker-compose up --build patient-service
docker-compose up --build referral-service
```

### Build Spring Boot JARs locally (without Docker)
```bash
cd patient-service && mvn package -DskipTests
cd referral-service && mvn package -DskipTests
```

### Run a single Spring Boot service locally (outside Docker)
```bash
cd patient-service && mvn spring-boot:run
```

### Obtain a JWT for testing
```bash
curl -s -X POST http://localhost:8090/realms/trucare/protocol/openid-connect/token \
  -d "client_id=trucare-gateway" \
  -d "client_secret=trucare-secret-local" \
  -d "username=dr.mehta" \
  -d "password=doctor123" \
  -d "grant_type=password" | jq -r .access_token
```

### Call KrakenD with a token
```bash
TOKEN=$(curl -s ... | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/patients
```

## Service Port Map

| Service          | Port  | Access              |
|------------------|-------|---------------------|
| KrakenD          | 8080  | External entry point |
| Keycloak Admin   | 8090  | Identity provider   |
| Patient Service  | 8081  | Direct (testing)    |
| Referral Service | 8082  | Direct (testing)    |

## Architecture

### Request Flow
```
Client → KrakenD (8080)
  → validates JWT against Keycloak JWKS (RS256)
  → strips raw token, propagates claims as headers:
      X-User-Id, X-User-Email, X-User-Role, X-User-Name
  → routes to Patient Service (8081) or Referral Service (8082)
       → JwtClaimsInterceptor reads X-User-* headers
       → stores in UserContextHolder (ThreadLocal)
       → Controller reads UserContext for audit/role logic
```

### KrakenD Configuration (`krakend/krakend.json`)
All API Gateway behavior lives here: endpoint definitions, backend URLs, JWT validator plugin (`auth/validator`), rate limiting, and circuit breaker. JWT claims are mapped to propagated headers using the `propagate_headers` field on the validator. Modify this file to add/change endpoints, auth requirements, or backend routing.

### Identity: Keycloak (`keycloak/realm/trucare-realm.json`)
Realm `trucare` is auto-imported at container start. Contains roles (DOCTOR, NURSE, ADMIN, PATIENT), pre-configured users, and the `trucare-gateway` OAuth2 client. The `realm-roles-mapper` protocol mapper injects the user's realm role into the JWT as the `role` claim.

### Spring Boot Microservices
Both `patient-service` and `referral-service` share the same structural pattern:
- **No Spring Security** — JWT validation is fully delegated to KrakenD
- **JwtClaimsInterceptor** — HandlerInterceptor that extracts `X-User-*` headers and stores a `UserContext` record in a ThreadLocal (`UserContextHolder`). Always cleared in `afterCompletion` to avoid Tomcat thread reuse issues.
- **In-memory data** — no database; data is hardcoded `List.of(...)` in the `*Service` class
- **GlobalExceptionHandler** (`@RestControllerAdvice`) — centralizes error responses

### Docker Compose Health Chain
Keycloak → (patient-service, referral-service) → KrakenD. Each service waits for its dependencies to pass health checks before starting.

## Test Users (Keycloak)

| Username     | Password   | Role   |
|--------------|------------|--------|
| dr.mehta     | doctor123  | DOCTOR |
| nurse.priya  | nurse123   | NURSE  |
| admin        | admin123   | ADMIN  |

## Key Files

| File | Purpose |
|------|---------|
| `krakend/krakend.json` | All API Gateway config — endpoints, auth, routing |
| `keycloak/realm/trucare-realm.json` | Keycloak realm, users, roles, OAuth2 client |
| `docker-compose.yml` | Full local stack orchestration |
| `patient-service/src/main/java/com/trucare/interceptor/` | JWT claim propagation pattern |
| `patient-service/src/main/java/com/trucare/model/UserContext.java` | JWT claims as Java record |

## Tutorial Progression (Git Branches)

| Branch | Content |
|--------|---------|
| `dev/krakend-step2/endpoints` | Basic KrakenD endpoints |
| `dev/krakend-step3-aggregation` | Response aggregation across services |
| `dev/krakend-step4-auth` | JWT auth via Keycloak (current) |
