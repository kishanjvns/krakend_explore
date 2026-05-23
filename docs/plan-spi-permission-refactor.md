# Mediq — SPI + Permission Refactor Plan

## Objective

Replace the tight Keycloak coupling in user-service with a Keycloak User Storage
Federation SPI. Simultaneously replace the in-memory and Keycloak-API-backed
permission system with a persisted `role_permissions` DB table, with resolved
permissions injected into the JWT at login time.

---

## Decisions Confirmed

| # | Decision |
|---|---|
| 1 | Keycloak User Storage Federation SPI — reads users directly from mediq DB at login |
| 2 | `role_permissions` table in user-service DB — single source of truth for fine-grained permissions |
| 3 | Permissions injected into JWT at login (Option A) — all services enforce from JWT, no cross-service calls |
| 4 | Keycloak master realm `admin`/`admin` — Platform team only, never used by application code |
| 5 | Application admin — real row in `users` table, seeded via Flyway migration |

---

## Architecture After This Change

```
Registration:
  Client → POST /users/patients/register
         → user-service saves to DB (password BCrypt-hashed)
         → publishes USER_REGISTERED event via outbox
         [No Keycloak API call]

Login:
  Client → Keycloak /token
         → Keycloak calls SPI → JDBC SELECT from mediq_users DB
         → SPI verifies BCrypt password
         → SPI returns UserModel with userId attribute = our UUID
         → Keycloak script mapper reads role_permissions table
         → JWT issued:
           {
             "sub":         "f:{spi-id}:{our-uuid}",   ← Keycloak internal
             "userId":      "550e8400-...",              ← our UUID (claim)
             "email":       "patient@mediq.com",
             "role":        "PATIENT",
             "permissions": ["READ_OWN_PROFILE", "WRITE_OWN_APPOINTMENT", ...]
           }

Request:
  Client → KrakenD (JWT validated, claims propagated as headers)
         → X-User-Id: 550e8400-...
         → X-User-Email: patient@mediq.com
         → X-User-Role: PATIENT
         → X-User-Permissions: READ_OWN_PROFILE,WRITE_OWN_APPOINTMENT,...
         → downstream services enforce @PreAuthorize from JWT headers
         [No cross-service calls for auth]

Deactivation:
  Admin → PUT /api/v1/admin/users/{id}/deactivate
        → user-service sets is_active=false in DB
        → SPI's isEnabled() returns false on next login
        → Keycloak rejects login automatically
        [No Keycloak Admin API call]
```

---

## What Gets Built (New)

### 1. `keycloak-user-spi/` — New Maven Module

```
keycloak-user-spi/
├── pom.xml
└── src/main/java/com/mediq/keycloak/spi/
    ├── MediqUserStorageProviderFactory.java   ← creates provider, reads config
    ├── MediqUserStorageProvider.java          ← getUserByUsername, getUserByEmail,
    │                                             getUserById, isValid (BCrypt check)
    ├── MediqUserAdapter.java                  ← wraps DB row as Keycloak UserModel,
    │                                             getId() returns our UUID,
    │                                             getRealmRoleMappings() returns user_type
    └── MediqUserStorageConstants.java         ← config keys: db.url, db.user,
                                                  db.password, db.schema
```

**Dependencies (scope=provided):**
- `keycloak-core`, `keycloak-server-spi`, `keycloak-server-spi-private`
- `jbcrypt` (BCrypt verification — no Spring dependency)
- `postgresql` JDBC driver
- `HikariCP` (connection pooling)

**Keycloak ConfigProperty (Admin UI configurable):**
- `db.url` — e.g. `jdbc:postgresql://postgres-service:5432/mediq_users`
- `db.user` — e.g. `mediq`
- `db.password` — e.g. `mediq`
- `db.schema` — e.g. `mediq_users`

**SPI queries:**
```sql
-- getUserByUsername / getUserByEmail
SELECT u.id, u.first_name, u.last_name, u.is_active, u.user_type,
       uc.contact_value AS email,
       up.password_hash
FROM mediq_users.users u
JOIN mediq_users.user_contact uc ON uc.user_id = u.id
                                 AND uc.contact_type = 'EMAIL'
                                 AND uc.is_primary = true
LEFT JOIN mediq_users.user_password up ON up.user_id = u.id
WHERE uc.contact_value = ?   -- or u.id = ?

-- getUserById (called by Keycloak after federation prefix strip)
SELECT ... WHERE u.id = ?::uuid
```

**`MediqUserAdapter` key methods:**
```java
getId()                  → our UUID string (Keycloak prepends federation prefix internally)
getUsername()            → primary email
getEmail()               → primary email
isEnabled()              → user.is_active
getFirstName()           → user.first_name
getLastName()            → user.last_name
getRealmRoleMappings()   → Set of RoleModel matching user.user_type
getAttributes()          → Map with "userId" → [our UUID string]
```

### 2. Keycloak Token Mapper (Script or Built-in Mapper)

Configured in mediq realm → `mediq-frontend-spa` client → Mappers:

**Mapper A — userId claim** (built-in User Attribute mapper):
- Attribute name: `userId`
- Token claim name: `userId`
- Add to: access token + id token

**Mapper B — permissions claim** (Script Protocol Mapper or Hardcoded mapper per role):

Option: Use a JavaScript protocol mapper in Keycloak that reads the `role_permissions`
table via the same JDBC connection and injects `permissions[]` into the JWT.

```javascript
// Keycloak script mapper
var role = user.getRealmRoleMappings().iterator().next().getName();
var permissions = queryPermissionsForRole(role); // JDBC call
token.setOtherClaims("permissions", permissions);
```

> **Note:** Keycloak scripting must be enabled (`--features=scripts` in Keycloak startup).
> Alternative: use a second SPI class implementing `ProtocolMapperSpi` baked into the
> same JAR — this is the production-safe approach.

### 3. `role_permissions` DB Table (new Flyway migration)

```sql
-- V4__spi_and_permissions.sql

-- 1. Add password_hash column to users
ALTER TABLE mediq_users.users
  ADD COLUMN password_hash VARCHAR(255);

-- 2. Create role_permissions table
CREATE TABLE mediq_users.role_permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name   VARCHAR(20) NOT NULL,
    permission  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (role_name, permission)
);

-- 3. Seed default role-permission mappings
INSERT INTO mediq_users.role_permissions (role_name, permission) VALUES
  ('PATIENT', 'READ_OWN_PROFILE'), ('PATIENT', 'WRITE_OWN_PROFILE'),
  ('PATIENT', 'READ_DOCTORS'), ('PATIENT', 'READ_DOCTOR_AVAILABILITY'),
  ('PATIENT', 'WRITE_OWN_APPOINTMENT'), ('PATIENT', 'READ_OWN_APPOINTMENT'),
  ('PATIENT', 'CANCEL_OWN_APPOINTMENT'), ('PATIENT', 'READ_OWN_NOTIFICATIONS'),
  ('PATIENT', 'SEND_OTP'), ('PATIENT', 'VERIFY_OTP'),

  ('DOCTOR', 'READ_OWN_PROFILE'), ('DOCTOR', 'WRITE_OWN_PROFILE'),
  ('DOCTOR', 'READ_PATIENT_PROFILE'), ('DOCTOR', 'READ_OWN_APPOINTMENT'),
  ('DOCTOR', 'WRITE_APPOINTMENT_SLOT'), ('DOCTOR', 'CONFIRM_APPOINTMENT'),
  ('DOCTOR', 'CANCEL_APPOINTMENT'), ('DOCTOR', 'READ_EMR'),
  ('DOCTOR', 'WRITE_EMR'), ('DOCTOR', 'READ_OWN_ANALYTICS'),
  ('DOCTOR', 'READ_OWN_NOTIFICATIONS'),

  ('NURSE', 'READ_OWN_PROFILE'), ('NURSE', 'READ_PATIENT_PROFILE'),
  ('NURSE', 'READ_OWN_APPOINTMENT'), ('NURSE', 'WRITE_OWN_APPOINTMENT'),
  ('NURSE', 'CANCEL_APPOINTMENT'), ('NURSE', 'READ_EMR'),
  ('NURSE', 'READ_OWN_NOTIFICATIONS'),

  ('ADMIN', 'READ_OWN_PROFILE'), ('ADMIN', 'READ_ANY_PROFILE'),
  ('ADMIN', 'WRITE_ANY_PROFILE'), ('ADMIN', 'VERIFY_DOCTOR'),
  ('ADMIN', 'DEACTIVATE_USER'), ('ADMIN', 'READ_DOCTORS'),
  ('ADMIN', 'READ_PATIENT_PROFILE'), ('ADMIN', 'READ_OWN_APPOINTMENT'),
  ('ADMIN', 'READ_ANY_APPOINTMENT'), ('ADMIN', 'CANCEL_ANY_APPOINTMENT'),
  ('ADMIN', 'WRITE_APPOINTMENT_SLOT'), ('ADMIN', 'READ_EMR'),
  ('ADMIN', 'WRITE_EMR'), ('ADMIN', 'READ_ANALYTICS'),
  ('ADMIN', 'READ_ANY_NOTIFICATIONS'), ('ADMIN', 'MANAGE_ROLES'),
  ('ADMIN', 'SEND_OTP'), ('ADMIN', 'VERIFY_OTP');

-- 4. Seed admin user (password = BCrypt of "admin123")
INSERT INTO mediq_users.users (id, user_type, first_name, last_name, is_active, is_verified, password_hash)
VALUES (
  gen_random_uuid(),
  'ADMIN',
  'System',
  'Admin',
  true,
  true,
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCKMXsGKfkF8...' -- BCrypt of "admin123"
);

-- 5. Seed admin email contact
INSERT INTO mediq_users.user_contact (user_id, contact_type, contact_value, is_primary, is_verified)
SELECT id, 'EMAIL', 'admin@mediq.com', true, true
FROM mediq_users.users WHERE user_type = 'ADMIN';

-- 6. Drop keycloak_id column (after SPI is live — do in separate migration)
-- V5__drop_keycloak_id.sql
-- ALTER TABLE mediq_users.users DROP COLUMN keycloak_id;
-- DROP INDEX idx_users_keycloak_id;
```

> `keycloak_id` drop is a **separate migration** (V5) deployed only after the SPI is
> confirmed working in production. This allows rollback without data loss.

### 4. New Admin Endpoints in user-service

```
PUT  /api/v1/admin/users/{id}/role       → update user_type in DB (role change)
PUT  /api/v1/admin/users/{id}/activate   → set is_active = true
PUT  /api/v1/admin/users/{id}/deactivate → set is_active = false
GET  /api/v1/admin/users                 → list all users (paginated)
```

---

## What Gets Deleted — Full Stale Code Inventory

All files and code below become dead weight post-implementation and must be
removed as part of the same PR, not left as follow-up.

### Files — Hard Delete

| File | Location | Why stale |
|------|----------|-----------|
| `KeycloakAdminClient.java` | `user-service/com/mediq/keycloak/` | SPI handles all auth; no Keycloak API calls remain |
| `KeycloakSyncConsumer.java` | `user-service/com/mediq/keycloak/` | Kafka listener that synced users to Keycloak — entirely replaced by SPI |
| `UserEventPublisher.java` | `user-service/com/mediq/event/` | `UserService` uses `saveToOutbox()` exclusively; direct Kafka publish is superseded. Verify no other caller before deleting. |
| `com/mediq/keycloak/` package | `user-service` | Empty directory after the two deletions above — remove the package |

### Methods — Delete from Existing Files

| Method | File | Why stale |
|--------|------|-----------|
| `getUserByKeycloakId(String keycloakId)` | `UserService.java` | Replaced by `getUserById(UUID)` — the `userId` JWT claim is our UUID |
| `findByKeycloakId(String keycloakId)` | `UserRepository.java` | No callers after `getUserByKeycloakId` is gone |
| `getAdminToken()` | `PermissionAdminService.java` | Entire Keycloak REST API path in this class is deleted |
| `getRolePermissions(String roleName)` | `PermissionAdminService.java` | Replaced by DB query |

### Fields — Remove from Existing Classes

| Field | Class | Why stale |
|-------|-------|-----------|
| `keycloakId` field + getter/setter | `UserEntity.java` | No longer stored; SPI owns identity |
| `keycloakId` parameter in `UserEvent.of()` | `UserEvent.java` | Not part of domain events anymore |
| `keycloakId` record component | `UserResponse.java` | Must not be exposed in API responses |
| `DEFAULT_ROLE_PERMISSIONS` map | `MediqPermissions.java` | Replaced by `role_permissions` DB table seeded in V4 |
| `keycloakId` in response map | `AuthController.java` | `me()` currently returns `Map.of("keycloakId", ...)` — remove this key |
| `@RequestHeader("X-Keycloak-Id")` param | `AuthController.java` | Header renamed to `X-User-Sub`; this specific header no longer propagated with the old name |
| `@Value("${mediq.keycloak.admin-url/username/password}")` | `PermissionAdminService.java` | Keycloak API path deleted |
| `restTemplate` field | `PermissionAdminService.java` | No HTTP calls remain after rewrite |

### Code in `UserService.buildEvent()` — Signature Change

```java
// Current — passes keycloakId:
return UserEvent.of(eventType, user.getId().toString(),
    user.getKeycloakId(),   // ← DELETE this argument
    user.getUserType().name(), ...);

// After — keycloakId removed from UserEvent.of():
return UserEvent.of(eventType, user.getId().toString(),
    user.getUserType().name(), ...);
```

### Plan Inconsistency Fixed — SPI Query

The SPI section previously referenced a `user_password` table that does not exist.
The correct query uses `password_hash` on the `users` table directly (added in V4):

```sql
-- Correct SPI lookup query
SELECT u.id, u.first_name, u.last_name, u.is_active, u.user_type,
       u.password_hash,
       uc.contact_value AS email
FROM mediq_users.users u
JOIN mediq_users.user_contact uc ON uc.user_id = u.id
                                 AND uc.contact_type = 'EMAIL'
                                 AND uc.is_primary = true
WHERE uc.contact_value = ?        -- getUserByUsername/getUserByEmail
   OR u.id = ?::uuid              -- getUserById
```

---

## Impact by System

---

### user-service — HIGH IMPACT

#### Files Deleted
- `com/mediq/keycloak/KeycloakAdminClient.java`
- `com/mediq/keycloak/KeycloakSyncConsumer.java`

#### Files Modified

**`UserEntity.java`**
- Remove `keycloakId` field and getter/setter
- Add `passwordHash` field (`@Column(name = "password_hash")`)

**`UserResponse.java` (DTO)**
- Remove `keycloakId` field — it must not be exposed in API responses

**`UserEvent.java`**
- Remove `keycloakId` field
- Update `UserEvent.of()` factory method signature

**`UserService.java`**
- Remove `KeycloakAdminClient` injection and all calls to it
- Remove OTP send from within `@Transactional` scope (already wrapped in try-catch — clean it up)
- `getUserByKeycloakId(String keycloakId)` → **rename to** `getUserById(UUID userId)`
  - Was: `findByKeycloakId(keycloakId)` 
  - Now: `findById(userId)` (the JWT `userId` claim IS our UUID via SPI)
- `deactivateUser()` — remove Keycloak sync (SPI handles it via `is_active` column)
- `verifyDoctor()` — no change needed (already no Keycloak call)

**`UserController.java`**
- `getMe()`: change `@RequestHeader("X-Keycloak-Id")` → `@RequestHeader("X-User-Id")`
  - Call `userService.getUserById(UUID.fromString(userId))` instead of `getUserByKeycloakId`

**`UserRepository.java`**
- Remove `findByKeycloakId(String keycloakId)` method

**`UserMapper.java`**
- Remove `keycloakId` mapping in `toResponse()`

**`PermissionAdminService.java`** — **Full rewrite**
- DELETE all Keycloak REST API calls (`getAdminToken`, `getRolePermissions`, `updateRolePermissions` via Keycloak)
- REPLACE with `RolePermissionsRepository` (JPA) reading/writing `role_permissions` table
- `getAllRolePermissions()` → `SELECT * FROM role_permissions GROUP BY role_name`
- `updateRolePermissions(roleName, permissions)` → DELETE old rows + INSERT new rows (transactional)
- Remove `@Value("${mediq.keycloak.admin-url}")` injection
- Remove `@Value("${KEYCLOAK_ADMIN_USERNAME}")` and `KEYCLOAK_ADMIN_PASSWORD`

**`MediqPermissions.java`**
- Keep `ALL_PERMISSIONS` list (still used for validation of unknown permissions in `updateRolePermissions`)
- Delete `DEFAULT_ROLE_PERMISSIONS` map (replaced by DB seed in V4 migration)

**`application.properties`**
- Remove:
  ```
  mediq.keycloak.admin-url
  mediq.keycloak.realm
  mediq.keycloak.admin-username
  mediq.keycloak.admin-password
  ```

**`UserOutboxEntity` / outbox pattern**
- No change — outbox continues to publish `USER_REGISTERED`, `DOCTOR_VERIFIED`, `USER_DEACTIVATED` events for downstream services
- `UserEventPublisher.java` — remove the `// TODO: Implement Outbox pattern` comment;
  evaluate if direct publish is still needed anywhere or if all publishing goes through outbox

#### New Files
- `RolePermission.java` — JPA entity for `role_permissions` table
- `RolePermissionsRepository.java` — JPA repository
- New admin endpoints in `UserController.java` or a new `AdminUserController.java`:
  - `PUT /admin/users/{id}/role`
  - `PUT /admin/users/{id}/activate`
  - `PUT /admin/users/{id}/deactivate`

#### Helm/k8s (user-service)
- `helm/services/user-service/values.yaml` — remove:
  ```yaml
  KEYCLOAK_ADMIN_URL: ...
  KEYCLOAK_ADMIN_USERNAME: ...
  KEYCLOAK_ADMIN_PASSWORD: ...
  ```

---

### Keycloak — HIGH IMPACT

#### New: Custom Dockerfile
```dockerfile
FROM quay.io/keycloak/keycloak:26
COPY keycloak-user-spi/target/keycloak-user-spi-1.0.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build --features=scripts
```

#### `mediq-realm.json` Changes
- **Remove** the `users` array entirely (admin, dr.mehta, nurse.priya)
  — they now live in our DB and the SPI provides them to Keycloak
- **Keep** `roles.realm` array unchanged (ADMIN, DOCTOR, NURSE, PATIENT)
- **Keep** `clients` array unchanged (`mediq-frontend-spa`, `mediq-test-cli`)
- **Add** User Storage Federation configuration pointing to the SPI
- **Add** two protocol mappers to `mediq-frontend-spa`:
  1. User Attribute mapper: `userId` attribute → `userId` JWT claim
  2. Script mapper (or custom SPI mapper): reads `role_permissions` → `permissions[]` JWT claim

#### `helm/infrastructure/keycloak/values.yaml`
- Update image to use the custom built image (`mediq/keycloak-spi:latest`)
- Add SPI DB connection env vars:
  ```yaml
  SPI_DB_URL: jdbc:postgresql://postgres-service:5432/mediq_users
  SPI_DB_USER: mediq
  SPI_DB_PASSWORD: mediq
  SPI_DB_SCHEMA: mediq_users
  ```

---

### KrakenD — MEDIUM IMPACT

#### `krakend/partials/auth_any_role.tmpl` (and other auth partials)

**Current `propagate_claims`:**
```json
["sub",         "X-Keycloak-Id"],
["userId",      "X-User-Id"],
```

**After:**
```json
["sub",         "X-User-Sub"],     ← rename, internal Keycloak federation ID
["userId",      "X-User-Id"],      ← our UUID from SPI attribute, unchanged
```

`X-Keycloak-Id` header is consumed by `UserController.getMe()` — that usage is
being replaced by `X-User-Id`. The rename removes confusion.

**No other KrakenD changes required.** The `permissions` claim propagation is
already wired: `["permissions", "X-User-Permissions"]`.

---

### doctor-service — LOW IMPACT

#### `doctor/event/UserEvent.java`
- Remove `keycloakId` field

#### `doctor/model/DoctorProfileEntity.java`
- Check: if `keycloakId` is stored here, remove the field and column
- `DoctorService.createDoctorStub(event)` — verify it does not use `event.keycloakId()`
  and update if it does

#### `doctor/event/UserEventConsumer.java`
- No logic change — consumer does not use `keycloakId`
- Recompile against updated `UserEvent` record

---

### appointment-service — LOW IMPACT

#### `appointment/event/UserEvent.java`
- Remove `keycloakId` field

#### `appointment/event/UserEventConsumer.java`
- No logic change — consumer does not use `keycloakId`

#### `appointment/event/DoctorEventConsumer.java`
- No change

---

### notification-service — LOW IMPACT

#### notification `UserEvent.java` (in event package)
- Remove `keycloakId` field

#### `UserEventConsumer.java`
- No logic change — sends welcome notification, does not use `keycloakId`

---

### Frontend (Angular) — LOW-MEDIUM IMPACT

#### `auth/auth.service.ts`

**Current:**
```typescript
public get keycloakId(): string { return this.claims['sub'] || ''; }
```
With SPI, `sub` is the Keycloak federation prefix ID, not our UUID.
Our UUID is now in the `userId` JWT claim, propagated as `X-User-Id`.

**After:**
```typescript
public get userId(): string {
  // userId claim (injected by SPI token mapper) = our UUID
  return this.claims['userId'] || this.currentUser()?.id || '';
}
// Remove or rename keycloakId getter — no longer meaningful to frontend
```

**`loadCurrentUser()`:**
```typescript
private async loadCurrentUser() {
  // GET /api/v1/users/me — now looks up by userId from X-User-Id header
  // No change to the call, only the server-side lookup changes
  const user = await this.http.get<UserResponse>(`${API_BASE}/users/me`).toPromise();
  this.currentUser.set(user ?? null);
}
```

**`permissions` from JWT:**
```typescript
public get permissions(): string[] {
  const p = this.claims['permissions'];
  return Array.isArray(p) ? p : (typeof p === 'string' ? p.split(',') : []);
}

public hasPermission(permission: string): boolean {
  return this.permissions.includes(permission);
}
```

**`UserResponse` model (`core/models.ts`):**
- Remove `keycloakId` field from the interface

#### Other components
- No change — components use `authService.userId`, `authService.role`,
  `authService.isAdmin()` etc. — these continue to work.

---

### Redis — NO IMPACT

Redis is used for:
- OTP storage (`otp:{userId}:{otp}`) — key uses our UUID, unchanged
- Token blacklist (`token:blacklist:{jti}`) — uses JWT `jti` claim, unchanged
- User cache (`user:{userId}`) — uses our UUID, unchanged

No changes required.

---

### Kafka — LOW IMPACT

#### Schema change: `UserEvent` (all services)

Remove `keycloakId` field from the `UserEvent` record in all four services.

**Backward compatibility:** Jackson ignores unknown fields by default. During the
rolling deployment window when some consumers are on old code and some on new, the
field being absent in the event will not break old consumers (they get null for the
field). Safe to roll out.

#### `KeycloakSyncConsumer` consumer group removed

The `mediq-keycloak-sync-group` consumer group in user-service is deleted.
The Kafka topic `mediq.user.events` continues to exist; other consumer groups
(doctor-sync, appointment-user-sync, notification-user-group) are unaffected.

#### `UserEventPublisher.java` — TODO resolved

The `// TODO: Implement Outbox pattern in M2.x` comment is resolved.
`UserService` already uses the outbox (`saveToOutbox()`). `UserEventPublisher`'s
direct Kafka send is superseded. Evaluate: if nothing else calls
`UserEventPublisher.publish()` directly, delete it; if used by OTP or other
flows, keep and clean up the TODO.

---

## Migration Strategy (Phased)

### Phase 1 — DB + SPI (No user-facing change)
1. Create `keycloak-user-spi/` Maven module, implement and test locally
2. Run `V4__spi_and_permissions.sql` migration (adds `password_hash`, `role_permissions`, seeds admin, seeds existing users' hashes)
3. Build custom Keycloak Docker image with SPI JAR
4. Deploy new Keycloak image — configure User Storage Federation in admin UI pointing to mediq DB
5. **Test:** existing seeded users (dr.mehta, nurse.priya, admin) can still log in via SPI
6. **Verify:** JWT contains `userId`, `role`, and `permissions[]` claims

### Phase 2 — user-service cleanup
1. Delete `KeycloakAdminClient.java`, `KeycloakSyncConsumer.java`
2. Update `UserEntity`, `UserEvent`, `UserResponse` (remove `keycloakId`)
3. Rewrite `PermissionAdminService` to use `role_permissions` DB table
4. Update `UserController.getMe()` to use `X-User-Id` header
5. Add new admin endpoints (`/admin/users/{id}/role`, `/activate`, `/deactivate`)
6. Update `application.properties` — remove keycloak admin properties
7. Rebuild and redeploy user-service

### Phase 3 — downstream services + frontend
1. Update `UserEvent` records in doctor-service, appointment-service, notification-service
2. Update frontend `AuthService` (userId claim, permissions helper, remove keycloakId)
3. Update KrakenD `propagate_claims` (rename `X-Keycloak-Id` → `X-User-Sub`)
4. Redeploy all affected services

### Phase 4 — cleanup (after Phase 1-3 confirmed stable)
1. Run `V5__drop_keycloak_id.sql` — drop `keycloak_id` column and index
2. Remove `mediq-test-cli` from realm JSON if no longer needed
3. Remove seeded users from `mediq-realm.json` (they now come from SPI)

---

## Risk & Rollback

| Risk | Mitigation |
|------|-----------|
| SPI JDBC pool exhaustion under load | HikariCP with maxPoolSize=5, connectionTimeout=3000ms |
| BCrypt hash mismatch (existing users) | V4 migration generates correct hashes for seeded users; new registrations always BCrypt |
| `sub` claim format change breaks frontend | `userId` claim is the stable identifier; frontend switches to `userId` not `sub` |
| `keycloak_id` column drop loses data | Phase 4 is optional and separate; column nullable after Phase 1, dropped only after full validation |
| Permission table empty after migration | V4 INSERT seeds all default mappings; idempotent (`UNIQUE` constraint) |
| Rolling deploy: old consumers get `UserEvent` without `keycloakId` | Jackson ignores missing fields — safe |

---

## File Change Summary

| File | Action |
|------|--------|
| `keycloak-user-spi/` (entire module) | CREATE |
| `helm/infrastructure/keycloak/` | MODIFY (new image, SPI config env vars) |
| `helm/infrastructure/keycloak/realm/mediq-realm.json` | MODIFY (remove users array, add mappers) |
| `user-service/.../KeycloakAdminClient.java` | DELETE |
| `user-service/.../KeycloakSyncConsumer.java` | DELETE |
| `user-service/.../UserEventPublisher.java` | DELETE (outbox is the publisher; verify no other callers first) |
| `user-service/com/mediq/keycloak/` (package) | DELETE directory (empty after above deletions) |
| `user-service/.../UserEntity.java` | MODIFY (remove keycloakId, add passwordHash) |
| `user-service/.../UserEvent.java` | MODIFY (remove keycloakId) |
| `user-service/.../UserResponse.java` | MODIFY (remove keycloakId) |
| `user-service/.../UserService.java` | MODIFY (remove Keycloak calls, rename getMe lookup) |
| `user-service/.../UserController.java` | MODIFY (getMe uses X-User-Id, new admin endpoints) |
| `user-service/.../UserRepository.java` | MODIFY (remove findByKeycloakId) |
| `user-service/.../UserMapper.java` | MODIFY (remove keycloakId mapping) |
| `user-service/.../PermissionAdminService.java` | REWRITE (DB-backed, no Keycloak API, delete getAdminToken/restTemplate) |
| `user-service/.../MediqPermissions.java` | MODIFY (delete DEFAULT_ROLE_PERMISSIONS map, keep ALL_PERMISSIONS) |
| `user-service/.../AuthController.java` | MODIFY (remove keycloakId from response map, fix X-Keycloak-Id header ref) |
| `user-service/.../RolePermissionDto.java` | VERIFY (still valid against rewritten PermissionAdminService) |
| `user-service/.../RolePermission.java` | CREATE (JPA entity) |
| `user-service/.../RolePermissionsRepository.java` | CREATE |
| `user-service/db/migration/V4__spi_and_permissions.sql` | CREATE |
| `user-service/db/migration/V5__drop_keycloak_id.sql` | CREATE (Phase 4 only) |
| `user-service/resources/application.properties` | MODIFY (remove keycloak admin props) |
| `helm/services/user-service/values.yaml` | MODIFY (remove KEYCLOAK_ADMIN_* vars) |
| `doctor-service/.../UserEvent.java` | MODIFY (remove keycloakId) |
| `doctor-service/.../DoctorProfileEntity.java` | MODIFY (remove keycloakId if present) |
| `appointment-service/.../UserEvent.java` | MODIFY (remove keycloakId) |
| `notification-service/.../UserEvent.java` | MODIFY (remove keycloakId) |
| `krakend/partials/auth_any_role.tmpl` | MODIFY (rename X-Keycloak-Id → X-User-Sub) |
| `frontend/.../auth.service.ts` | MODIFY (userId from claim, permissions helper) |
| `frontend/.../core/models.ts` | MODIFY (remove keycloakId from UserResponse) |
