# mediq — Task M1 Bug Fixes
## Branch
```powershell
# Already on feature/mediq-m1-user-service
git checkout feature/mediq-m1-user-service
```

5 targeted fixes only. Do NOT refactor anything else.
Make each change exactly as described, nothing more.

---

## Fix 1 — Add CREATE SCHEMA to Flyway Migration

**File:** `user-service/src/main/resources/db/migration/V1__create_user_schema.sql`

Add this as the VERY FIRST LINE of the file, before any CREATE TABLE statement:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_users;
```

The file must now start exactly like this:

```sql
CREATE SCHEMA IF NOT EXISTS mediq_users;

-- Core user identity (Patient, Doctor, Admin)
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ...
```

Do not change anything else in the file.

**Why this matters:**
PostgreSQL cannot create tables inside a schema that does not exist.
Flyway runs this migration BEFORE JPA initialises.
Without this line, the service fails at startup with:
`ERROR: schema "mediq_users" does not exist`

---

## Fix 2 — Delete patient-service folder

Run this from `D:\codebase\krakend_explore`:

```powershell
Remove-Item -Recurse -Force patient-service
```

Verify it is gone:
```powershell
ls
# Expected output — patient-service should NOT appear:
# docker-compose.yml
# keycloak/
# krakend/
# referral-service/
# user-service/
# TASK-M1-user-service.md
# TASK-M1-FIXES.md
```

**Why this matters:**
The refactor is complete. All patient-service code now lives in user-service.
Keeping both causes confusion. The old service is no longer referenced
in docker-compose so it will never run — but the folder is misleading.

---

## Fix 3 — Update KrakenD hosts.json

**File:** `krakend/settings/hosts.json`

Replace the ENTIRE file content with:

```json
{
  "user_service": ["http://user-service:8081"],
  "referral_service": ["http://referral-service:8082"]
}
```

**Why this matters:**
KrakenD uses this file to resolve service hostnames.
The old file referenced `patient-service` which no longer exists.
KrakenD would start but all routing to the user endpoints would fail
with a connection error to a non-existent container.

---

## Fix 4 — Delete old Keycloak realm file

Run this from `D:\codebase\krakend_explore`:

```powershell
Remove-Item keycloak\realm\trucare-realm.json
```

Verify only one realm file remains:
```powershell
ls keycloak\realm\
# Expected:
# mediq-realm.json
```

**Why this matters:**
Keycloak imports ALL `.json` files from `/opt/keycloak/data/import` on startup.
If both `trucare-realm.json` and `mediq-realm.json` are present,
Keycloak creates TWO realms: `trucare` and `mediq`.
The `trucare` realm has a `trucare-gateway` client that conflicts
with the mediq configuration and confuses KrakenD JWT validation.

---

## Fix 5 — Add @Transactional to getPendingDoctorVerifications

**File:** `user-service/src/main/java/com/mediq/service/UserService.java`

Find this method (around line 80-87):

```java
public List<UserResponse> getPendingDoctorVerifications() {
    return doctorProfileRepository
        .findByVerificationStatus(VerificationStatus.PENDING)
        .stream()
        .map(p -> userMapper.toResponse(p.getUser()))
        .toList();
}
```

Add `@Transactional(readOnly = true)` annotation above the method:

```java
@Transactional(readOnly = true)
public List<UserResponse> getPendingDoctorVerifications() {
    return doctorProfileRepository
        .findByVerificationStatus(VerificationStatus.PENDING)
        .stream()
        .map(p -> userMapper.toResponse(p.getUser()))
        .toList();
}
```

Do not change anything else in UserService.java.

**Why this matters:**
`doctorProfileRepository.findByVerificationStatus()` returns `DoctorProfileEntity` objects.
Each has a `@OneToOne(fetch = FetchType.LAZY)` relationship to `UserEntity`.
`p.getUser()` accesses that lazy relationship.

Without `@Transactional`, the Hibernate session is closed before `.map()` executes.
Accessing a lazy relationship outside a session throws:
`org.hibernate.LazyInitializationException: could not initialize proxy - no Session`

With `@Transactional(readOnly = true)`:
- Session stays open for the entire method
- `p.getUser()` is fetched within the open session
- `readOnly = true` tells Hibernate not to check for dirty entities → slightly faster

---

## Fix 6 — Version the Redis Cache Key

**File:** `user-service/src/main/java/com/mediq/service/UserCacheService.java`

Find this line:

```java
private static final String KEY_PREFIX = "user:";
```

Change it to:

```java
private static final String KEY_PREFIX = "user:v1:";
```

Do not change anything else in UserCacheService.java.

**Why this matters:**
If `UserResponse` gains a new field in a future task,
old cached entries in Redis will have a different JSON shape.
Jackson deserialization will fail on those old entries.

With versioned keys:
- When `UserResponse` changes → bump `"user:v1:"` to `"user:v2:"`
- All old v1 keys are ignored (different prefix)
- Old entries expire naturally via their TTL (30 minutes)
- New entries cached fresh with the new shape
- Zero errors. Zero downtime. Zero manual cleanup.

The existing `try/catch` in `get()` already handles deserialization failures
gracefully (returns null → fallback to DB). The key versioning is an
additional layer that prevents cache misses entirely during model evolution.

---

## Verification After All Fixes

### 1. Build compiles
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

Watch for these in the logs:

```
mediq-postgres    | database system is ready to accept connections
mediq-kafka       | started (kafka.server.KafkaServer)
mediq-keycloak    | Keycloak 24.0.3 on JVM (powered by Quarkus)
mediq-user-service| Flyway: Successfully applied 1 migration to schema "mediq_users"
mediq-user-service| Started UserServiceApplication in X.XXX seconds
```

### 3. Verify Flyway ran schema creation
```powershell
# Connect to postgres
docker exec -it mediq-postgres psql -U mediq -d mediq_users

# Inside psql:
\dn
# Expected: mediq_users schema listed

\dt mediq_users.*
# Expected:
# mediq_users | users          | table
# mediq_users | user_address   | table
# mediq_users | user_contact   | table
# mediq_users | doctor_profile | table
# mediq_users | flyway_schema_history | table

\q
```

### 4. Register a patient
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

# Expected: HTTP 201 with UserResponse JSON
# Check: id is a UUID, keycloakId is null (sync not yet complete), userType: PATIENT
```

### 5. Verify Kafka event was published
```powershell
# From WSL terminal:
docker exec -it mediq-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic mediq.user.events \
  --from-beginning \
  --max-messages 3
```

Expected output — a JSON event like:
```json
{
  "eventId": "uuid-here",
  "eventType": "USER_REGISTERED",
  "userId": "uuid-here",
  "keycloakId": null,
  "userType": "PATIENT",
  "firstName": "Rahul",
  "lastName": "Sharma",
  "primaryEmail": "rahul@example.com",
  "primaryPhone": "9876543210",
  "verificationStatus": null,
  "occurredAt": "2024-..."
}
```

### 6. Verify Redis cache
```powershell
# After doing a GET /users/{userId} call:
# First call → cache miss → DB hit → cached
# Second call → cache hit → no DB hit

# Check Redis directly:
docker exec -it mediq-redis redis-cli
> KEYS user:v1:*
# Expected: one key per user you fetched
> GET user:v1:{userId}
# Expected: JSON of UserResponse
> TTL user:v1:{userId}
# Expected: number between 1 and 1800 (30 min in seconds)
```

### 7. Check only mediq realm in Keycloak
```
Open browser: http://localhost:8090
Login: admin / admin
Left sidebar → should show ONLY "mediq" realm
"trucare" realm should NOT appear
```

---

## Commit After Verification

```powershell
cd D:\codebase\krakend_explore
git add .
git commit -m "fix(m1): apply 6 post-review fixes to user-service

- Add CREATE SCHEMA to Flyway migration
- Remove patient-service (refactor complete)
- Update KrakenD hosts.json to user-service
- Remove trucare-realm.json (mediq realm only)
- Add @Transactional to getPendingDoctorVerifications
- Version Redis cache key to user:v1:"
```

---

## Known Remaining TODOs (do not fix now — future tasks)

```
1. KeycloakAdminClient.getAdminToken() returns TODO string
   → Keycloak sync will fail but app will not crash
   → Fix in M2.x: implement client_credentials grant flow

2. UserEventPublisher: Kafka publish can fail after DB save
   → Fix in M2.x: Outbox pattern with Debezium CDC

3. No duplicate email validation
   → Fix when adding doctor-service

4. KrakenD partials still reference old endpoint patterns
   → Fix when adding new KrakenD routes for user-service endpoints
```
