# mediq — Final Helmfile Runtime Fixes

## Branch
```powershell
git checkout feature/mediq-helmfile
```

## Status of All Bugs

```
✅ Bug 1 — Keycloak ConfigMap missing                    ALREADY FIXED
✅ Bug 2 — KrakenD ConfigMap missing                     ALREADY FIXED
✅ Bug 3 — Postgres only creates 1 DB                    ALREADY FIXED

❌ Bug 4 — Jaeger host wrong in krakend.tmpl             FIX BELOW
❌ Bug 5 — Auth partials reference "trucare" realm        FIX BELOW
❌ Bug 6 — hosts.json missing 3 required keys             FIX BELOW
❌ Bug 7 — User registration endpoints missing from KrakenD  FIX BELOW
```

---

## Fix 4 — Jaeger hostname in krakend.tmpl

**File:** `helm/gateway/krakend/config/krakend.tmpl`

Find:
```json
"host": "jaeger",
```

Replace with:
```json
"host": "jaeger-service",
```

**Why:** Kubernetes DNS resolves services by their Service name within the same namespace.
The Jaeger Service is named `jaeger-service`. The string `"jaeger"` resolves to nothing.
KrakenD would fail to export traces silently — no error, just no traces in Jaeger UI.

---

## Fix 5 — Auth partials realm name

### File 1: `helm/gateway/krakend/config/partials/auth_doctor_admin.tmpl`

Find (2 occurrences):
```
/realms/trucare/
```

Replace both with:
```
/realms/mediq/
```

Full corrected file:
```json
"auth/validator": {
  "alg": "RS256",
  "jwk_url": "{{ .hosts.keycloak_internal }}/realms/mediq/protocol/openid-connect/certs",
  "issuer": "{{ .hosts.keycloak_external }}/realms/mediq",
  "cache": true,
  "cache_duration": 900,
  "disable_jwk_security": true,
  "roles_key": "role",
  "roles": ["DOCTOR", "ADMIN"],
  "propagate_claims": [
    ["sub",   "X-User-Id"],
    ["email", "X-User-Email"],
    ["role",  "X-User-Role"],
    ["name",  "X-User-Name"]
  ]
}
```

### File 2: `helm/gateway/krakend/config/partials/auth_doctor_nurse_admin.tmpl`

Same change — replace both occurrences of `trucare` with `mediq`:

```json
"auth/validator": {
  "alg": "RS256",
  "jwk_url": "{{ .hosts.keycloak_internal }}/realms/mediq/protocol/openid-connect/certs",
  "issuer": "{{ .hosts.keycloak_external }}/realms/mediq",
  "cache": true,
  "cache_duration": 900,
  "disable_jwk_security": true,
  "roles_key": "role",
  "roles": ["DOCTOR", "NURSE", "ADMIN"],
  "propagate_claims": [
    ["sub",   "X-User-Id"],
    ["email", "X-User-Email"],
    ["role",  "X-User-Role"],
    ["name",  "X-User-Name"]
  ]
}
```

**Why:** KrakenD fetches Keycloak's public key to validate JWT signatures.
`trucare` realm no longer exists — only `mediq` realm exists.
Every JWT validation call fails → ALL protected endpoints return 401.

---

## Fix 6 — Add missing keys to hosts.json

**File:** `helm/gateway/krakend/config/settings/hosts.json`

Replace entire file:
```json
{
  "user_service":         ["http://user-service:8081"],
  "patient_service":      ["http://user-service:8081"],
  "referral_service":     ["http://referral-service:8082"],
  "doctor_service":       ["http://doctor-service:8083"],
  "appointment_service":  ["http://appointment-service:8084"],
  "emr_service":          ["http://emr-service:8086"],
  "analytics_service":    ["http://analytics-service:8087"],
  "keycloak_internal":    "http://keycloak-service:8090",
  "keycloak_external":    "http://localhost:8090"
}
```

**Why each key:**

```
patient_service:
  endpoint_patients.tmpl uses {{ .hosts.patient_service }}
  Old name before rename — adding alias pointing to user-service
  Both resolve to same pod: http://user-service:8081

keycloak_internal:
  Used in auth partials for jwk_url (JWT public key fetch)
  KrakenD pod fetches JWK from INSIDE the cluster
  Must use K8s Service name: keycloak-service:8090
  This is pod-to-pod traffic — uses ClusterDNS

keycloak_external:
  Used in auth partials for issuer validation
  JWT tokens carry "iss" claim = URL Keycloak used when issuing token
  Keycloak issues tokens with issuer = what the CLIENT sees externally
  In kind: clients reach Keycloak via localhost:8090 (NodePort 30090)
  So issuer in JWT = http://localhost:8090/realms/mediq
  KrakenD must match this exactly — any mismatch = 401

  IMPORTANT: If you see 401 after this fix:
  Decode your JWT at jwt.io → check "iss" field
  Copy that exact value as keycloak_external in hosts.json
```

**Also update:** `krakend/settings/hosts.json` (the docker-compose version) — add same keys so docker-compose dev mode also works:

```json
{
  "user_service":         ["http://user-service:8081"],
  "patient_service":      ["http://user-service:8081"],
  "referral_service":     ["http://referral-service:8082"],
  "doctor_service":       ["http://doctor-service:8083"],
  "appointment_service":  ["http://appointment-service:8084"],
  "emr_service":          ["http://emr-service:8086"],
  "analytics_service":    ["http://analytics-service:8087"],
  "keycloak_internal":    "http://keycloak:8090",
  "keycloak_external":    "http://localhost:8090"
}
```

Note: docker-compose uses `keycloak` (container name), Helm uses `keycloak-service` (K8s Service name).

---

## Fix 7 — Add missing user registration endpoints to KrakenD

**The problem:**

```
User-service has these REST endpoints:
  POST /users/patients/register   ← patient registration (public)
  POST /users/doctors/register    ← doctor registration (public)
  GET  /users/{userId}            ← get user profile (protected)
  DELETE /users/{userId}          ← deactivate user (protected)
  GET  /users/doctors/pending-verification  ← admin only
  PUT  /users/doctors/{doctorUserId}/verify ← admin only

KrakenD currently routes NONE of these.
Calling POST http://localhost:8080/api/v1/users/patients/register
→ 404 Not Found from KrakenD
→ Registration completely unreachable through the gateway
```

### Step 1: Create endpoint_users.tmpl

Create `helm/gateway/krakend/config/partials/endpoint_users.tmpl`:

```json
{
  "endpoint": "/api/v1/users/patients/register",
  "method": "POST",
  "output_encoding": "json",
  "backend": [{
    "url_pattern": "/users/patients/register",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/users/doctors/register",
  "method": "POST",
  "output_encoding": "json",
  "backend": [{
    "url_pattern": "/users/doctors/register",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/users/{userId}",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 300, "capacity": 300 },
    {{ template "auth_doctor_nurse_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/users/{userId}",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/users/{userId}",
  "method": "DELETE",
  "extra_config": {
    {{ template "auth_doctor_nurse_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/users/{userId}",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/users/doctors/pending-verification",
  "method": "GET",
  "extra_config": {
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/users/doctors/pending-verification",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/users/doctors/{doctorUserId}/verify",
  "method": "PUT",
  "extra_config": {
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/users/doctors/{doctorUserId}/verify",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
}
```

### Step 2: Add endpoint_users.tmpl to krakend.tmpl endpoints list

**File:** `helm/gateway/krakend/config/krakend.tmpl`

Find:
```json
"endpoints": [
  {{ template "endpoint_patients.tmpl" . }},
  {{ template "endpoint_referrals.tmpl" . }},
  {{ template "endpoint_doctors.tmpl" . }},
  {{ template "endpoint_appointments.tmpl" . }}
]
```

Replace with:
```json
"endpoints": [
  {{ template "endpoint_users.tmpl" . }},
  {{ template "endpoint_patients.tmpl" . }},
  {{ template "endpoint_referrals.tmpl" . }},
  {{ template "endpoint_doctors.tmpl" . }},
  {{ template "endpoint_appointments.tmpl" . }}
]
```

### Step 3: Add endpoint_users.tmpl to ConfigMap

**File:** `helm/gateway/krakend/templates/configmap.yaml`

Find:
```yaml
  rate_limit_proxy.tmpl: |
    {{- .Files.Get "config/partials/rate_limit_proxy.tmpl" | nindent 4 }}
```

Add the new entry BEFORE that line:
```yaml
  endpoint_users.tmpl: |
    {{- .Files.Get "config/partials/endpoint_users.tmpl" | nindent 4 }}
  rate_limit_proxy.tmpl: |
    {{- .Files.Get "config/partials/rate_limit_proxy.tmpl" | nindent 4 }}
```

### Step 4: Also create endpoint_users.tmpl for docker-compose krakend

Create `krakend/partials/endpoint_users.tmpl` with the same content as above.

Add to `krakend/krakend.tmpl` endpoints list:
```json
"endpoints": [
  {{ template "endpoint_users.tmpl" . }},
  {{ template "endpoint_patients.tmpl" . }},
  ...
]
```

---

## Verification

### 1. Check no "trucare" remains anywhere in helm krakend config
```powershell
Select-String -Path "helm\gateway\krakend\config\*" -Pattern "trucare" -Recurse
# Expected: NO output
```

### 2. Check Jaeger host is correct
```powershell
Select-String -Path "helm\gateway\krakend\config\krakend.tmpl" -Pattern "jaeger"
# Expected: "jaeger-service" appears, NOT bare "jaeger"
```

### 3. Check all 3 new hosts.json keys exist
```powershell
Get-Content "helm\gateway\krakend\config\settings\hosts.json"
# Expected: patient_service, keycloak_internal, keycloak_external all present
```

### 4. Check endpoint_users.tmpl exists
```powershell
ls helm\gateway\krakend\config\partials\
# Expected: endpoint_users.tmpl listed
```

### 5. Render krakend chart and validate
```powershell
helm template mediq-krakend ./helm/gateway/krakend --debug 2>&1 | Select-String "error|Error"
# Expected: no errors
```

### 6. Build, load, deploy
```powershell
# Build (PowerShell)
docker build -t mediq/user-service:latest ./user-service
docker build -t mediq/doctor-service:latest ./doctor-service
docker build -t mediq/appointment-service:latest ./appointment-service
docker build -t mediq/notification-service:latest ./notification-service
docker build -t mediq/emr-service:latest ./emr-service
docker build -t mediq/analytics-service:latest ./analytics-service
```

```bash
# Load to kind (WSL)
kind load docker-image mediq/user-service:latest --name kishan-lab
kind load docker-image mediq/doctor-service:latest --name kishan-lab
kind load docker-image mediq/appointment-service:latest --name kishan-lab
kind load docker-image mediq/notification-service:latest --name kishan-lab
kind load docker-image mediq/emr-service:latest --name kishan-lab
kind load docker-image mediq/analytics-service:latest --name kishan-lab
```

```powershell
# Deploy (PowerShell)
helmfile -e dev diff
helmfile -e dev apply
```

### 7. Watch pods come up
```powershell
kubectl get pods -n mediq -w
```

### 8. End-to-end test — patient registration
```powershell
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Test",
    "lastName": "Patient",
    "dateOfBirth": "1990-01-01",
    "password": "Test@1234",
    "contacts": [
      {"contactType": "EMAIL", "contactValue": "test@mediq.com", "isPrimary": true}
    ]
  }'
# Expected: HTTP 201 with UserResponse JSON (not 404, not 401)
```

### 9. Check Jaeger traces
```
Open: http://localhost:16686
Service: mediq-krakend
→ POST /api/v1/users/patients/register trace should appear
→ Spans: KrakenD → user-service → DB → Redis → Kafka
```

---

## Summary of All 7 Bugs

```
Bug 1 ✅ Keycloak ConfigMap missing        → Added configmap.yaml + realm file
Bug 2 ✅ KrakenD ConfigMap missing         → Added configmap.yaml with 3 ConfigMaps
Bug 3 ✅ Postgres only 1 DB                → Added init script ConfigMap
Bug 4 ❌ Jaeger host "jaeger"              → Change to "jaeger-service"
Bug 5 ❌ Auth partials "trucare" realm     → Change to "mediq" in both auth templates
Bug 6 ❌ hosts.json missing keys           → Add patient_service, keycloak_internal,
                                              keycloak_external
Bug 7 ❌ User endpoints not in KrakenD     → Create endpoint_users.tmpl + add to
                                              krakend.tmpl + add to ConfigMap
```

---

## Commit
```powershell
git add .
git commit -m "fix(helmfile): 4 runtime bugs in KrakenD config

Bug 4: Fix Jaeger host 'jaeger' → 'jaeger-service' (K8s DNS)
Bug 5: Fix auth partials realm 'trucare' → 'mediq' in both
       auth_doctor_admin.tmpl and auth_doctor_nurse_admin.tmpl
Bug 6: Add missing hosts.json keys:
       - patient_service (alias for user-service, used in old partials)
       - keycloak_internal (pod-to-pod JWK fetch)
       - keycloak_external (JWT issuer validation)
Bug 7: Add endpoint_users.tmpl with all user-service routes:
       - POST /api/v1/users/patients/register (public)
       - POST /api/v1/users/doctors/register  (public)
       - GET  /api/v1/users/{userId}          (protected)
       - DELETE /api/v1/users/{userId}        (protected)
       - GET  /api/v1/users/doctors/pending-verification (admin)
       - PUT  /api/v1/users/doctors/{id}/verify (admin)
       Added to krakend.tmpl endpoints list
       Added to ConfigMap krakend-partials"
```
