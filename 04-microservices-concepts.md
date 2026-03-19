# Microservices Concepts Revision — Interview & Concept Guide
### Topics from Step 2 Code Only

---

## 1. What is a Microservice?

A microservice is a **small, independently deployable service** that:
- Owns a single business capability (patient management, referral management)
- Has its own process and port
- Has its own data store (in production — shared DB is an anti-pattern)
- Communicates over the network (HTTP, messaging)
- Can be deployed, scaled, and failed independently of other services

### In this project
- `patient-service` owns everything about patients (port 8081)
- `referral-service` owns everything about referrals (port 8082)
- `krakend` is the API Gateway — not a business service

---

## 2. Single Responsibility per Service

Each service in this project manages exactly ONE domain entity.
`patient-service` never talks about referrals in its own data model.
`referral-service` stores `patientId` as a reference but does NOT
import or depend on `patient-service` code.

### Why this matters
If `referral-service` needs patient data, it calls `patient-service`'s API
(or KrakenD does it via aggregation — Step 3).
This prevents tight coupling. You can deploy `patient-service` v2
without touching `referral-service` at all.

---

## 3. Independent Deployability

Each service is a **completely independent Spring Boot application** with:
- Its own `main()` method and `@SpringBootApplication`
- Its own embedded Tomcat
- Its own `pom.xml` and dependencies
- Its own `Dockerfile` and build pipeline
- Its own port (`8081`, `8082`)

### In Docker Compose (and EKS)
Each service is built and run as a separate container.
Restarting or redeploying `referral-service` does not affect `patient-service`.

### In your EKS setup
Each service is a separate Kubernetes `Deployment` with its own:
- Pod template
- Resource limits (CPU/memory)
- HPA (horizontal pod autoscaler)
- Health probes

---

## 4. API Gateway Pattern

An API Gateway is a single entry point that sits in front of all microservices.

### Without gateway (client-side discovery — problematic)
```
Client → patient-service:8081/patients/P001
Client → referral-service:8082/referrals/patient/P001
Client → notification-service:8083/notifications/P001
```
- Client must know every service's host and port
- Client makes 3 round trips
- Each service needs its own auth, rate limiting, CORS handling
- Adding a new service requires client changes

### With KrakenD (server-side gateway — clean)
```
Client → KrakenD:8080/api/v1/patient-summary/P001
         KrakenD calls all 3 services in parallel internally
         KrakenD returns one merged JSON response
```
- Client knows only one URL
- One round trip
- Auth, rate limiting, CORS handled once at the gateway
- Adding a new service = adding config to `krakend.json`, no client changes

### Your TrueCare gateway flow
```
Route 53 → ALB → KrakenD Pod → patient-service Pod
                              → referral-service Pod
                              → notification-service Pod (future)
```

---

## 5. Separation of Internal vs External API

A critical pattern demonstrated in this project:

```
External (what clients see)          Internal (what KrakenD calls)
/api/v1/patients          ────────→  /patients
/api/v1/patients/{id}     ────────→  /patients/{id}
/api/v1/referrals         ────────→  /referrals
```

### Why this matters
- Internal URLs can change without breaking any client
- You can version (`/api/v1/`, `/api/v2/`) at the gateway level
- Services are not exposed to the internet — they only accept calls from KrakenD
- In EKS, services use `ClusterIP` type — not reachable outside the cluster

---

## 6. Domain Model Separation — DTO Pattern

In this project, each service has:
1. **Domain model** (`Patient`, `Referral`) — internal representation
2. **Response DTO** (`PatientResponse`, `ReferralResponse`) — API contract

### Why separate them?
```java
// Domain model — internal
public record Patient(String id, String name, int age,
                      String diagnosis, String assignedDoctor,
                      PatientStatus status) { }

// Response DTO — what crosses the service boundary
public record PatientResponse(String id, String name, int age,
                               String diagnosis, String assignedDoctor,
                               String status,     // string, not sealed interface
                               boolean isActive,  // computed field
                               String serviceSource) { }
```

- The `PatientStatus` sealed interface is an internal Java type — not suitable for JSON
- `PatientResponse` exposes `status` as a plain string and `isActive` as a derived boolean
- KrakenD receives `PatientResponse` JSON, not the internal `Patient` record
- You can add fields to `Patient` without changing `PatientResponse` (and vice versa)

---

## 7. Health Checks in Microservices

Every service exposes a health endpoint:
```
GET /actuator/health     → Spring Boot Actuator (JSON)
GET /patients/health     → custom simple check (also JSON)
```

### Why health checks matter in microservices
```yaml
# docker-compose.yml
healthcheck:
  test: ["CMD", "wget", "--spider", "http://localhost:8081/actuator/health"]
  interval: 20s
  retries: 5
```

- Docker will not start KrakenD until both services are healthy (`depends_on: condition: service_healthy`)
- In EKS: `livenessProbe` restarts unhealthy pods; `readinessProbe` removes them from load balancing
- ALB target group checks: if health check fails, ALB stops sending traffic to that pod

### In your TrueCare Route 53 → ALB flow
ALB sends health check requests to `/actuator/health` on each pod.
Only pods returning HTTP 200 receive live traffic. This is automatic
health-based traffic shifting with no manual intervention.

---

## 8. Shared Network — Docker vs Kubernetes

### In Docker Compose (local learning)
```yaml
networks:
  trucare-net:
    driver: bridge
```
All containers share this network. `patient-service` resolves as a hostname
via Docker's internal DNS. KrakenD calls `http://patient-service:8081`.

### In Kubernetes (your EKS setup)
Kubernetes creates a `ClusterIP` Service for each deployment:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: patient-service
spec:
  type: ClusterIP
  ports:
    - port: 8081
```
KrakenD calls: `http://patient-service.trucare.svc.cluster.local:8081`
or simply `http://patient-service:8081` (within the same namespace).

The concept is identical — service name as DNS hostname — just the
DNS resolver changes from Docker to CoreDNS.

---

## 9. Consistent Error Handling Across Services

Both services use the identical `ErrorResponse` shape:

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Patient not found with id: P999",
  "path": "/patients/P999",
  "timestamp": "2024-04-01T10:30:00Z"
}
```

### Why consistency matters
- KrakenD (Step 3) can parse errors predictably from any backend
- Circuit breaker (Step 5) uses HTTP status codes — consistent 404 vs 500 matters
- Debugging across distributed services is far easier with a standard shape
- Clients need only one error-handling routine regardless of which service failed

---

## 10. Stateless Services

Both services in this project are **completely stateless**:
- No session data stored on the server
- Each request carries all information needed to process it
- Any pod can handle any request (enables horizontal scaling)
- In-memory store is for learning only — production uses an external database

### Why stateless matters in EKS
If `patient-service` has 3 pods and one crashes, the remaining 2 immediately
serve all traffic. No session data is lost because there was none.
Stateless is a prerequisite for horizontal pod autoscaling (HPA).

---

## Common Interview Questions

**Q: What is the difference between a monolith and a microservice?**
A: A monolith is a single deployable unit with all functionality. Microservices split functionality into independently deployable services. Microservices enable independent scaling, deployment, and technology choices per service — at the cost of network complexity and distributed system challenges.

**Q: What is the API Gateway pattern?**
A: An API Gateway is a single entry point for all client requests. It handles cross-cutting concerns (auth, rate limiting, CORS, logging) centrally and can aggregate, transform, and route requests to internal services. Clients interact with one URL instead of knowing about every service.

**Q: Why do we separate the domain model from the response DTO?**
A: The domain model represents internal business logic (e.g. PatientStatus as a sealed interface). The DTO represents the API contract — what crosses the service boundary. Separating them means internal changes don't break the API contract, and the API can expose computed fields or simplified representations.

**Q: What is a health check and why does it matter?**
A: A health check endpoint confirms a service is alive and ready to serve traffic. Load balancers (ALB), container orchestrators (Kubernetes), and API gateways (KrakenD) use health checks to route traffic only to healthy instances and automatically restart or replace unhealthy ones.

**Q: Why should microservices be stateless?**
A: Stateless services allow any replica to handle any request, enabling horizontal scaling and zero-downtime rolling deployments. Stateful services require sticky sessions or distributed session stores, which add complexity and failure modes.
