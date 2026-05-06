# mediq — KrakenD Full Audit Fix (Final)

## Branch
```powershell
git checkout main
git pull origin main
git checkout -b fix/mediq-krakend-full-audit
```

## All 7 Bugs — Applied to Both Locations

```
Every fix applied in TWO places:
  1. krakend/                        ← docker compose
  2. helm/gateway/krakend/config/    ← Kubernetes / Helmfile
```

---

## FIX 1 — Jaeger host (docker-compose krakend.tmpl)

**File:** `krakend/krakend.tmpl`

Find:
```json
            "host": "jaeger-service",
```

Replace with:
```json
            "host": "{{ .env.JAEGER_HOST }}",
```

**File:** `docker-compose.yml` — find krakend service environment:

```yaml
      FC_ENABLE: "1"
      FC_TEMPLATES: "/etc/krakend/partials"
      FC_SETTINGS: "/etc/krakend/settings"
```

Add `JAEGER_HOST`:
```yaml
      FC_ENABLE: "1"
      FC_TEMPLATES: "/etc/krakend/partials"
      FC_SETTINGS: "/etc/krakend/settings"
      JAEGER_HOST: "jaeger"
```

**File:** `helm/gateway/krakend/config/krakend.tmpl`

Apply same `{{ .env.JAEGER_HOST }}` change.

**File:** `helm/gateway/krakend/values.yaml` — add:
```yaml
jaegerHost: "jaeger-service"
```

**File:** `helm/gateway/krakend/templates/deployment.yaml` — add to env:
```yaml
          - name: JAEGER_HOST
            value: {{ .Values.jaegerHost | quote }}
```

---

## FIX 2 — endpoint_patients.tmpl (REPLACE ENTIRE FILE)

**File:** `krakend/partials/endpoint_patients.tmpl`

Delete all content. Replace with:

```json
{
  "endpoint": "/api/v1/patient-summary/{id}",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": {
      "max_rate": 200,
      "capacity": 200
    },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [
    {
      "url_pattern": "/users/{id}",
      "host": ["{{ .hosts.user_service }}"],
      "encoding": "json",
      "allow": ["id", "firstName", "lastName", "userType", "active", "verified"],
      "extra_config": {
        {{ template "rate_limit_proxy.tmpl" . }},
        {{ template "circuit_breaker.tmpl" . }}
      }
    },
    {
      "url_pattern": "/referrals/patient/{id}",
      "host": ["{{ .hosts.referral_service }}"],
      "encoding": "json",
      "is_collection": true,
      "mapping": { "collection": "referrals" },
      "extra_config": {
        {{ template "rate_limit_proxy.tmpl" . }},
        {{ template "circuit_breaker.tmpl" . }}
      }
    }
  ]
}
```

What changed:
```
REMOVED: /api/v1/patients
REMOVED: /api/v1/patients/status/{status}
REMOVED: /api/v1/patients/active
REMOVED: /api/v1/patients/{id}
All called /patients/* which does not exist in user-service → 502

FIXED:   /api/v1/patient-summary/{id} first backend
         url_pattern /patients/{id} → /users/{id}
         allow list updated to match UserResponse fields
```

**File:** `helm/gateway/krakend/config/partials/endpoint_patients.tmpl`

Apply exact same replacement.

---

## FIX 3 — hosts.json add notification_service

**File:** `krakend/settings/hosts.json`

Replace entire file:
```json
{
  "user_service":          ["http://user-service:8081"],
  "patient_service":       ["http://user-service:8081"],
  "referral_service":      ["http://referral-service:8082"],
  "doctor_service":        ["http://doctor-service:8083"],
  "appointment_service":   ["http://appointment-service:8084"],
  "notification_service":  ["http://notification-service:8085"],
  "emr_service":           ["http://emr-service:8086"],
  "analytics_service":     ["http://analytics-service:8087"],
  "payment_service":       ["http://payment-service:8089"],
  "keycloak_internal":     "http://keycloak:8090",
  "keycloak_external":     "http://localhost:8090"
}
```

**File:** `helm/gateway/krakend/config/settings/hosts.json`

Replace entire file:
```json
{
  "user_service":          ["http://user-service:8081"],
  "patient_service":       ["http://user-service:8081"],
  "referral_service":      ["http://referral-service:8082"],
  "doctor_service":        ["http://doctor-service:8083"],
  "appointment_service":   ["http://appointment-service:8084"],
  "notification_service":  ["http://notification-service:8085"],
  "emr_service":           ["http://emr-service:8086"],
  "analytics_service":     ["http://analytics-service:8087"],
  "payment_service":       ["http://payment-service:8089"],
  "keycloak_internal":     "http://keycloak-service:8090",
  "keycloak_external":     "http://localhost:8090"
}
```

Note: `keycloak_internal` differs — `keycloak` in docker-compose, `keycloak-service` in Helm.

---

## FIX 4 — Create endpoint_emr.tmpl (NEW FILE)

Actual endpoints from `EmrController.java`:
```
POST /emr/patients/{patientId}/events/{eventType}
GET  /emr/patients/{patientId}/current
GET  /emr/patients/{patientId}/history
GET  /emr/patients/{patientId}/as-of?date=yyyy-MM-dd
```

**Create** `krakend/partials/endpoint_emr.tmpl`:

```json
{
  "endpoint": "/api/v1/emr/patients/{patientId}/events/{eventType}",
  "method": "POST",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 50, "capacity": 50 },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/emr/patients/{patientId}/events/{eventType}",
    "host": ["{{ .hosts.emr_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/emr/patients/{patientId}/current",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 200, "capacity": 200 },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/emr/patients/{patientId}/current",
    "host": ["{{ .hosts.emr_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/emr/patients/{patientId}/history",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 100, "capacity": 100 },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/emr/patients/{patientId}/history",
    "host": ["{{ .hosts.emr_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/emr/patients/{patientId}/as-of",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 50, "capacity": 50 },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/emr/patients/{patientId}/as-of",
    "host": ["{{ .hosts.emr_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
}
```

**Also create** `helm/gateway/krakend/config/partials/endpoint_emr.tmpl` — exact same content.

---

## FIX 5 — Create endpoint_analytics.tmpl (NEW FILE)

Actual endpoints from `AnalyticsDashboardController.java`:
```
GET /analytics/dashboard
GET /analytics/appointments/daily   (?from= &to= optional)
GET /analytics/doctors/performance  (?date= optional)
```

**Create** `krakend/partials/endpoint_analytics.tmpl`:

```json
{
  "endpoint": "/api/v1/analytics/dashboard",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 60, "capacity": 60 },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/analytics/dashboard",
    "host": ["{{ .hosts.analytics_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/analytics/appointments/daily",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 60, "capacity": 60 },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/analytics/appointments/daily",
    "host": ["{{ .hosts.analytics_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/analytics/doctors/performance",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 60, "capacity": 60 },
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/analytics/doctors/performance",
    "host": ["{{ .hosts.analytics_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
}
```

**Also create** `helm/gateway/krakend/config/partials/endpoint_analytics.tmpl` — exact same content.

---

## FIX 6 — Create endpoint_notifications.tmpl (NEW FILE)

Actual endpoints from `NotificationController.java`:
```
GET /notifications/user/{userId}
```

**Create** `krakend/partials/endpoint_notifications.tmpl`:

```json
{
  "endpoint": "/api/v1/notifications/user/{userId}",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 200, "capacity": 200 },
    {{ template "auth_doctor_nurse_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/notifications/user/{userId}",
    "host": ["{{ .hosts.notification_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
}
```

**Also create** `helm/gateway/krakend/config/partials/endpoint_notifications.tmpl` — exact same content.

---

## FIX 7a — Update krakend.tmpl endpoints list (docker-compose)

**File:** `krakend/krakend.tmpl`

Find:
```json
  "endpoints": [
    {{ template "endpoint_users.tmpl" . }},
    {{ template "endpoint_patients.tmpl" . }},
    {{ template "endpoint_referrals.tmpl" . }},
    {{ template "endpoint_doctors.tmpl" . }},
    {{ template "endpoint_appointments.tmpl" . }},
    {{ template "endpoint_payments.tmpl" . }}
  ]
```

Replace with:
```json
  "endpoints": [
    {{ template "endpoint_users.tmpl" . }},
    {{ template "endpoint_patients.tmpl" . }},
    {{ template "endpoint_referrals.tmpl" . }},
    {{ template "endpoint_doctors.tmpl" . }},
    {{ template "endpoint_appointments.tmpl" . }},
    {{ template "endpoint_payments.tmpl" . }},
    {{ template "endpoint_emr.tmpl" . }},
    {{ template "endpoint_analytics.tmpl" . }},
    {{ template "endpoint_notifications.tmpl" . }}
  ]
```

## FIX 7b — Update krakend.tmpl endpoints list (Helm)

**File:** `helm/gateway/krakend/config/krakend.tmpl`

Apply exact same replacement as FIX 7a.

---

## FIX 8 — Register new partials in Helm ConfigMap

**File:** `helm/gateway/krakend/templates/configmap.yaml`

Find:
```yaml
  rate_limit_proxy.tmpl: |
    {{- .Files.Get "config/partials/rate_limit_proxy.tmpl" | nindent 4 }}
```

Replace with:
```yaml
  endpoint_emr.tmpl: |
    {{- .Files.Get "config/partials/endpoint_emr.tmpl" | nindent 4 }}
  endpoint_analytics.tmpl: |
    {{- .Files.Get "config/partials/endpoint_analytics.tmpl" | nindent 4 }}
  endpoint_notifications.tmpl: |
    {{- .Files.Get "config/partials/endpoint_notifications.tmpl" | nindent 4 }}
  rate_limit_proxy.tmpl: |
    {{- .Files.Get "config/partials/rate_limit_proxy.tmpl" | nindent 4 }}
```

---

## Verification

### 1. Restart docker-compose
```powershell
docker compose down
docker compose up --build
```

### 2. No template errors in KrakenD logs
```powershell
docker logs mediq-krakend | grep -i "error\|panic" | head -10
# Expected: no errors
```

### 3. Verify Jaeger env var applied
```powershell
docker exec mediq-krakend env | grep JAEGER
# Expected: JAEGER_HOST=jaeger
```

### 4. Distributed tracing works — check Jaeger UI
```
http://localhost:16686
Service: mediq-krakend
→ Make a patient registration call → trace should appear
→ Was broken before this fix
```

### 5. Dead endpoints return 404 (not 502)
```powershell
curl http://localhost:8080/api/v1/patients
# Expected: {"status":404} — endpoint removed from KrakenD
```

### 6. patient-summary BFF works
```powershell
curl -H "Authorization: Bearer {doctorToken}" `
  http://localhost:8080/api/v1/patient-summary/{userId}
# Expected: 200 with user fields + referrals array merged
```

### 7. EMR endpoints reachable
```powershell
curl -X POST "http://localhost:8080/api/v1/emr/patients/{patientId}/events/DIAGNOSIS_ADDED" `
  -H "Authorization: Bearer {doctorToken}" `
  -H "Content-Type: application/json" `
  -d '{"payload":{"diagnoseCode":"E11","description":"Type 2 Diabetes"},"recordedBy":null}'
# Expected: 201 Created

curl -H "Authorization: Bearer {doctorToken}" `
  "http://localhost:8080/api/v1/emr/patients/{patientId}/current"
# Expected: 200 with patient summary
```

### 8. Analytics endpoints reachable
```powershell
curl -H "Authorization: Bearer {adminToken}" `
  http://localhost:8080/api/v1/analytics/dashboard
# Expected: 200 with platformMetrics + todayAppointments
```

### 9. Notification history reachable
```powershell
curl -H "Authorization: Bearer {token}" `
  "http://localhost:8080/api/v1/notifications/user/{userId}"
# Expected: 200 with notification list
```

---

## Complete Endpoint Map — After All Fixes

```
user-service (8081):
  POST /api/v1/users/patients/register             PUBLIC
  POST /api/v1/users/doctors/register              PUBLIC
  GET  /api/v1/users/{userId}                      PATIENT/DOCTOR/ADMIN
  DELETE /api/v1/users/{userId}                    DOCTOR/NURSE/ADMIN
  GET  /api/v1/users/doctors/pending-verification  DOCTOR/ADMIN
  PUT  /api/v1/users/doctors/{id}/verify           DOCTOR/ADMIN
  POST /api/v1/users/{userId}/send-otp             PUBLIC
  POST /api/v1/users/{userId}/verify-otp           PUBLIC

referral-service (8082):
  GET /api/v1/referrals                            DOCTOR/ADMIN
  GET /api/v1/referrals/open                       DOCTOR/NURSE/ADMIN
  GET /api/v1/referrals/patient/{patientId}        DOCTOR/NURSE/ADMIN
  GET /api/v1/referrals/{referralId}               DOCTOR/NURSE/ADMIN

BFF (multi-service):
  GET /api/v1/patient-summary/{id}                 DOCTOR/ADMIN
  GET /api/v1/web/patient-overview/{patientId}     DOCTOR/NURSE/ADMIN
  GET /api/v1/web/doctor-overview/{doctorId}       DOCTOR/ADMIN
  GET /api/v1/mobile/doctors/{doctorId}            DOCTOR/NURSE/ADMIN

doctor-service (8083):
  GET /api/v1/doctors/search                       PUBLIC
  GET /api/v1/doctors/{doctorId}                   DOCTOR/NURSE/ADMIN
  GET /api/v1/doctors/{doctorId}/availability      PUBLIC

appointment-service (8084):
  POST /api/v1/appointments                        DOCTOR/NURSE/ADMIN
  GET  /api/v1/appointments/{appointmentId}        DOCTOR/NURSE/ADMIN
  PUT  /api/v1/appointments/{id}/confirm           DOCTOR/ADMIN
  PUT  /api/v1/appointments/{id}/cancel            DOCTOR/NURSE/ADMIN
  POST /api/v1/slots                               DOCTOR/ADMIN

notification-service (8085):  ← NEW
  GET /api/v1/notifications/user/{userId}          DOCTOR/NURSE/ADMIN

emr-service (8086):  ← NEW
  POST /api/v1/emr/patients/{id}/events/{type}    DOCTOR/ADMIN
  GET  /api/v1/emr/patients/{id}/current           DOCTOR/ADMIN
  GET  /api/v1/emr/patients/{id}/history           DOCTOR/ADMIN
  GET  /api/v1/emr/patients/{id}/as-of             DOCTOR/ADMIN

analytics-service (8087):  ← NEW
  GET /api/v1/analytics/dashboard                  DOCTOR/ADMIN
  GET /api/v1/analytics/appointments/daily         DOCTOR/ADMIN
  GET /api/v1/analytics/doctors/performance        DOCTOR/ADMIN

payment-service (8089):
  POST /api/v1/payments/intent                     DOCTOR/NURSE/ADMIN
  GET  /api/v1/payments/{paymentId}                DOCTOR/NURSE/ADMIN
```

---

## Commit
```powershell
git add .
git commit -m "fix: KrakenD full audit — 7 bugs fixed, 3 services added

Bug 1: Jaeger host JAEGER_HOST env var
  docker-compose: JAEGER_HOST=jaeger (service name)
  Helm: jaegerHost=jaeger-service (K8s service name)
  Distributed tracing now works in both environments

Bug 2: endpoint_patients.tmpl cleaned up
  Removed 4 dead /patients/* endpoints (502 on every call)
  Fixed patient-summary BFF: /patients/{id} → /users/{id}

Bug 3: notification_service added to hosts.json (both versions)

Bug 4: endpoint_emr.tmpl created
  4 endpoints — POST events, GET current/history/as-of

Bug 5: endpoint_analytics.tmpl created
  3 endpoints — dashboard, daily appointments, doctor performance

Bug 6: endpoint_notifications.tmpl created
  1 endpoint — notification history per user

Bug 7: krakend.tmpl updated in both docker-compose and Helm
  Added 3 new template includes
  Helm ConfigMap registered all 3 new partials"
```
