# KrakenD Step 2 — Manual Guide: First Config & Endpoints

---

## What You Build Manually

The entire backend code (patient-service, referral-service) is generated.
Your job is to create **`krakend/krakend.json`** from scratch, step by step.

---

## Backend Endpoints Reference

Use these when writing your `krakend.json` backend blocks.

### patient-service — internal host: `http://patient-service:8081`

| Method | Internal Path                  | Description                  |
|--------|--------------------------------|------------------------------|
| GET    | `/patients`                    | All patients                 |
| GET    | `/patients/{id}`               | Patient by ID                |
| GET    | `/patients/status/{status}`    | Patients by status           |
| GET    | `/patients/active`             | Active patients only         |
| GET    | `/actuator/health`             | Health probe                 |

### referral-service — internal host: `http://referral-service:8082`

| Method | Internal Path                      | Description                  |
|--------|------------------------------------|------------------------------|
| GET    | `/referrals`                       | All referrals                |
| GET    | `/referrals/{referralId}`          | Referral by ID               |
| GET    | `/referrals/patient/{patientId}`   | Referrals for a patient      |
| GET    | `/referrals/status/{status}`       | Referrals by status          |
| GET    | `/referrals/open`                  | Open referrals only          |
| GET    | `/actuator/health`                 | Health probe                 |

---

## Step A — Create the skeleton

Create folder `krakend/` in the project root.
Inside it create `krakend.json` with this skeleton:

```json
{
  "$schema": "https://www.krakend.io/schema/v2.7/krakend.json",
  "version": 3,
  "name": "TrueCare API Gateway",
  "port": 8080,
  "timeout": "3000ms",
  "cache_ttl": "300s",
  "endpoints": []
}
```

**Key fields explained:**
- `version: 3` — KrakenD v2 config format (not the same as the Docker image version)
- `port: 8080` — the port KrakenD listens on inside the container
- `timeout` — maximum time KrakenD waits for a backend response
- `cache_ttl` — default cache time for GET responses (can be overridden per endpoint)
- `endpoints` — this is where ALL your routing config goes

---

## Step B — First endpoint: GET all patients

Add inside `"endpoints": []`:

```json
{
  "endpoint": "/api/v1/patients",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/patients",
      "host": ["http://patient-service:8081"],
      "encoding": "json"
    }
  ]
}
```

**Why `host` is an array:**
KrakenD supports multiple hosts per backend for round-robin load balancing.
Even with one host you must use an array.

**Why the hostname is `patient-service` not `localhost`:**
Inside Docker Compose all containers share `trucare-net` network.
KrakenD resolves `patient-service` via Docker's internal DNS to the container IP.
`localhost` inside the KrakenD container means KrakenD itself — not your service.

---

## Step C — Path parameter endpoint: GET patient by ID

Append after the previous endpoint (don't forget the comma):

```json
{
  "endpoint": "/api/v1/patients/{id}",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/patients/{id}",
      "host": ["http://patient-service:8081"],
      "encoding": "json"
    }
  ]
}
```

**Path parameter rule:**
The `{id}` placeholder must appear in BOTH `endpoint` and `url_pattern`.
KrakenD extracts the value from the incoming URL and injects it into the backend call.
This is how `GET /api/v1/patients/P001` becomes `GET http://patient-service:8081/patients/P001`.

---

## Step D — Status filter: GET patients by status

```json
{
  "endpoint": "/api/v1/patients/status/{status}",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/patients/status/{status}",
      "host": ["http://patient-service:8081"],
      "encoding": "json"
    }
  ]
}
```

---

## Step E — Active patients endpoint

```json
{
  "endpoint": "/api/v1/patients/active",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/patients/active",
      "host": ["http://patient-service:8081"],
      "encoding": "json"
    }
  ]
}
```

---

## Step F — All referral endpoints

Add these four endpoints for the referral-service:

```json
{
  "endpoint": "/api/v1/referrals",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/referrals",
      "host": ["http://referral-service:8082"],
      "encoding": "json"
    }
  ]
},
{
  "endpoint": "/api/v1/referrals/open",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/referrals/open",
      "host": ["http://referral-service:8082"],
      "encoding": "json"
    }
  ]
},
{
  "endpoint": "/api/v1/referrals/patient/{patientId}",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/referrals/patient/{patientId}",
      "host": ["http://referral-service:8082"],
      "encoding": "json"
    }
  ]
},
{
  "endpoint": "/api/v1/referrals/{referralId}",
  "method": "GET",
  "backend": [
    {
      "url_pattern": "/referrals/{referralId}",
      "host": ["http://referral-service:8082"],
      "encoding": "json"
    }
  ]
}
```

---

## Final krakend.json structure

Your completed file should look like:

```
{
  "$schema": ...,
  "version": 3,
  "name": "TrueCare API Gateway",
  "port": 8080,
  "timeout": "3000ms",
  "cache_ttl": "300s",
  "endpoints": [
    { patient all },
    { patient by id },
    { patient by status },
    { patient active },
    { referrals all },
    { referrals open },
    { referrals by patient },
    { referral by id }
  ]
}
```

---

## Run Everything

```bash
# Start all containers (builds Java services on first run — takes ~2 min)
docker-compose up --build

# Watch logs from KrakenD only
docker-compose logs -f krakend

# Stop everything
docker-compose down
```

---

## Test All Routes

```bash
# ── Patient Service via KrakenD ──────────────────────────────────────────────
curl http://localhost:8080/api/v1/patients
curl http://localhost:8080/api/v1/patients/P001
curl http://localhost:8080/api/v1/patients/P003
curl http://localhost:8080/api/v1/patients/status/admitted
curl http://localhost:8080/api/v1/patients/status/discharged
curl http://localhost:8080/api/v1/patients/active

# ── Referral Service via KrakenD ─────────────────────────────────────────────
curl http://localhost:8080/api/v1/referrals
curl http://localhost:8080/api/v1/referrals/open
curl http://localhost:8080/api/v1/referrals/R001
curl http://localhost:8080/api/v1/referrals/patient/P001

# ── Direct backend calls (bypass KrakenD — for debugging only) ───────────────
curl http://localhost:8081/patients
curl http://localhost:8081/patients/P003
curl http://localhost:8082/referrals
curl http://localhost:8082/referrals/patient/P001
```

---

## Validation Checklist

- [ ] `docker-compose up --build` completes without errors
- [ ] All 3 containers show healthy in `docker ps`
- [ ] `curl localhost:8080/api/v1/patients/P003` returns Ramesh Gupta (critical)
- [ ] `curl localhost:8080/api/v1/patients/status/admitted` returns filtered list
- [ ] `curl localhost:8080/api/v1/referrals/patient/P001` returns R001 and R004
- [ ] `curl localhost:8080/api/v1/referrals/open` returns only pending/approved
- [ ] KrakenD debug logs show backend requests being forwarded
- [ ] `curl localhost:8080/api/v1/patients/UNKNOWN` returns 404 (not 500)

---

## Biggest Mistake at Step 2

**Using `localhost` in the KrakenD `host` field.**
Inside Docker, `localhost` means the KrakenD container itself.
Always use the Docker Compose service name: `http://patient-service:8081`

---

## Free Resources

| Resource | URL |
|---|---|
| KrakenD Quickstart | https://www.krakend.io/docs/overview/quickstart/ |
| KrakenD Endpoint config | https://www.krakend.io/docs/endpoints/ |
| KrakenD Designer (visual builder) | https://designer.krakend.io |
| KrakenD GitHub examples | https://github.com/krakend/krakend-ce/tree/master/examples |
| YouTube: KrakenD Tutorial (Scalable Scripts) | Search "KrakenD API Gateway Tutorial Scalable Scripts" |
