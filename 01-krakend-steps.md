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


# KrakenD Step 4 — Manual Guide: JWT Validation & Auth

---

## How KrakenD JWT Validation Works

```
Client sends:  Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

KrakenD:
  1. Extracts the Bearer token from the Authorization header
  2. Fetches the Cognito JWKS (public keys) — cached after first fetch
  3. Verifies: signature, expiry (exp), issuer (iss), audience (aud)
  4. If VALID:   strips the raw token, injects claim headers, forwards request
  5. If INVALID: returns HTTP 401 immediately — backend never called
```

The raw JWT **never reaches** your Spring Boot services.
KrakenD forwards only decoded claims as plain headers:

```
X-User-Id    → JWT "sub" claim   (Cognito user UUID)
X-User-Email → JWT "email" claim
X-User-Role  → JWT custom claim  (e.g. DOCTOR, NURSE, ADMIN)
X-User-Name  → JWT "name" claim
```

---

## Step A — Understand JWT structure

A JWT has 3 parts separated by dots:
```
eyJhbGciOiJSUzI1NiJ9   ← Header  (algorithm: RS256)
.eyJzdWIiOiJVMDAxIn0   ← Payload (claims: sub, email, exp, iss...)
.SflKxwRJSMeKKF2QT4fw  ← Signature (signed with Cognito private key)
```

KrakenD uses the **public key** from Cognito's JWKS endpoint to verify
the signature — proving the token was issued by your Cognito user pool
and has not been tampered with.

---

## Step B — Get your Cognito JWKS URL

Your Cognito JWKS URL follows this pattern:
```
https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
```

Example for TrueCare:
```
https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_XXXXXXXX/.well-known/jwks.json
```

Visit this URL in a browser — you will see the public keys JSON.
KrakenD fetches and caches this automatically.

For local learning (without real Cognito), use a mock JWKS — see Step F below.

---

## Step C — Add global JWT validation to krakend.json

Add this at the root level of `krakend.json` (outside `endpoints`):

```json
{
  "$schema": "https://www.krakend.io/schema/v2.7/krakend.json",
  "version": 3,
  "name": "TrueCare API Gateway",
  "port": 8080,
  "timeout": "3000ms",
  "cache_ttl": "300s",

  "extra_config": {
    "auth/validator": {
      "alg": "RS256",
      "jwk_url": "https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_XXXXXXXX/.well-known/jwks.json",
      "cache": true,
      "cache_duration": 900,
      "disable_jwk_security": false
    }
  },

  "endpoints": [...]
}
```

🔎 **Key fields explained:**

`alg: RS256` — the signing algorithm Cognito uses. RS256 = RSA + SHA256 (asymmetric).
KrakenD verifies with the public key. Never use HS256 (symmetric) with a third-party IdP.

`jwk_url` — where KrakenD fetches Cognito's public keys from.
KrakenD calls this once and caches the response.

`cache: true` — cache the JWKS response locally inside KrakenD.
Without this, KrakenD calls Cognito on every single request — huge latency.

`cache_duration: 900` — re-fetch JWKS every 900 seconds (15 minutes).
Cognito rotates keys periodically — this ensures KrakenD picks up new keys.

`disable_jwk_security: false` — keep TLS verification when fetching JWKS.
Never set this to true in production.

---

## Step D — Protect specific endpoints

Not all endpoints need auth. Add `extra_config` with `auth/validator`
to each endpoint that requires a valid JWT:

```json
{
  "endpoint": "/api/v1/patients/{id}",
  "method": "GET",
  "extra_config": {
    "auth/validator": {
      "alg": "RS256",
      "jwk_url": "https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_XXXXXXXX/.well-known/jwks.json",
      "cache": true,
      "roles_key": "custom:role",
      "roles": ["DOCTOR", "NURSE", "ADMIN"],
      "propagate_claims": [
        ["sub",          "X-User-Id"],
        ["email",        "X-User-Email"],
        ["custom:role",  "X-User-Role"],
        ["name",         "X-User-Name"]
      ]
    }
  },
  "backend": [
    {
      "url_pattern": "/patients/{id}",
      "host": ["http://patient-service:8081"],
      "encoding": "json"
    }
  ]
}
```

🔎 **New fields explained:**

`roles_key: "custom:role"` — the JWT claim key that holds the user's role.
In Cognito this is a custom attribute. The value is compared against `roles`.

`roles: ["DOCTOR", "NURSE", "ADMIN"]` — only JWT tokens where `custom:role`
matches one of these values are allowed through.
Any other role → KrakenD returns HTTP 403 Forbidden.

`propagate_claims` — maps JWT claim keys to forwarded HTTP header names.
Format: `[["jwt_claim_key", "Header-Name"], ...]`
KrakenD extracts these claims from the validated token and adds them as
request headers before forwarding to your backend.

---

## Step E — Public vs protected endpoint design

```json
"endpoints": [

  {
    "endpoint": "/api/v1/patients",
    "method": "GET",
    "output_encoding": "no-op",
    "backend": [...]
    // NO extra_config auth/validator = PUBLIC endpoint
    // Anyone can call this — no JWT required
  },

  {
    "endpoint": "/api/v1/patients/{id}",
    "method": "GET",
    "extra_config": {
      "auth/validator": {
        "alg": "RS256",
        "jwk_url": "...",
        "cache": true,
        "roles_key": "custom:role",
        "roles": ["DOCTOR", "NURSE", "ADMIN"],
        "propagate_claims": [
          ["sub",         "X-User-Id"],
          ["email",       "X-User-Email"],
          ["custom:role", "X-User-Role"],
          ["name",        "X-User-Name"]
        ]
      }
    },
    "backend": [...]
    // PROTECTED — valid JWT with allowed role required
  },

  {
    "endpoint": "/api/v1/referrals",
    "method": "GET",
    "extra_config": {
      "auth/validator": {
        "alg": "RS256",
        "jwk_url": "...",
        "cache": true,
        "roles_key": "custom:role",
        "roles": ["DOCTOR", "ADMIN"],
        // NURSES cannot see all referrals — only DOCTOR and ADMIN
        "propagate_claims": [
          ["sub",         "X-User-Id"],
          ["email",       "X-User-Email"],
          ["custom:role", "X-User-Role"],
          ["name",        "X-User-Name"]
        ]
      }
    },
    "backend": [...]
  }
]
```

---

## Step F — Local testing without real Cognito (mock JWT)

For local Docker Compose testing, generate a mock JWT and mock JWKS:

### Option 1 — Use jwt.io to generate a test token

1. Go to https://jwt.io
2. Set algorithm: RS256
3. Add payload:
```json
{
  "sub": "U001",
  "email": "dr.mehta@trucare.com",
  "custom:role": "DOCTOR",
  "name": "Dr. Mehta",
  "exp": 9999999999,
  "iss": "https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_TEST"
}
```
4. Copy the generated token

### Option 2 — Disable JWT for local dev only

Add `"disable_jwk_security": true` and use a local mock JWKS server,
OR temporarily remove `auth/validator` from endpoints during local testing.

Never commit disabled security to main branch.

---

## Step G — Test JWT validation

```bash
# Protected endpoint WITHOUT token → should return 401
curl -v http://localhost:8080/api/v1/patients/P001

# Protected endpoint WITH valid token → should return 200
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8080/api/v1/patients/P001

# Protected endpoint with wrong role → should return 403
# (token valid but role not in allowed list)
curl -H "Authorization: Bearer NURSE_ROLE_TOKEN" \
     http://localhost:8080/api/v1/referrals

# Public endpoint (no token needed) → should return 200
curl http://localhost:8080/api/v1/patients
```

---

## Step H — Verify claim propagation in service logs

After adding the interceptor to your Spring Boot services,
check the logs when a protected request comes through:

```
INFO  c.t.c.PatientController - GET /patients/P001 accessed by userId=U001 role=DOCTOR
DEBUG c.t.i.JwtClaimsInterceptor - UserContext set: userId=U001, role=DOCTOR
DEBUG c.t.i.JwtClaimsInterceptor - UserContext cleared after request completion
```

If you see `userId=anonymous` on a protected endpoint, the
`propagate_claims` config in krakend.json is missing or misspelled.

---

## Endpoint auth matrix for TrueCare

| Endpoint | Auth required | Allowed roles |
|---|---|---|
| GET /api/v1/patients | No | Anyone |
| GET /api/v1/patients/active | No | Anyone |
| GET /api/v1/patients/status/{s} | No | Anyone |
| GET /api/v1/patients/{id} | Yes | DOCTOR, NURSE, ADMIN |
| GET /api/v1/referrals | Yes | DOCTOR, ADMIN |
| GET /api/v1/referrals/open | Yes | DOCTOR, NURSE, ADMIN |
| GET /api/v1/referrals/{id} | Yes | DOCTOR, NURSE, ADMIN |
| GET /api/v1/referrals/patient/{id} | Yes | DOCTOR, NURSE, ADMIN |
| GET /api/v1/patient-summary/{id} | Yes | DOCTOR, ADMIN |

---

## The `issuer` field — when and why you need it

🔎 **What it means**

`issuer` explicitly tells KrakenD what value to expect in the JWT `iss` claim.
Without it, KrakenD derives the expected issuer automatically from the `jwk_url` base URL.

⚙️ **How KrakenD derives issuer when not set**

```
jwk_url = http://keycloak:8090/realms/trucare/protocol/openid-connect/certs
                ↓ KrakenD strips the path
expected iss = http://keycloak:8090/realms/trucare
```

✅ **When you do NOT need `issuer`**

Production with Cognito or any cloud IdP — the token fetch URL and the JWKS URL
share the same domain so `iss` in the token matches what KrakenD derives automatically:

```
Cognito token endpoint:  https://cognito-idp.ap-south-1.amazonaws.com/pool/.../token
Token iss stamp:         https://cognito-idp.ap-south-1.amazonaws.com/pool
KrakenD jwk_url:         https://cognito-idp.ap-south-1.amazonaws.com/pool/.../jwks.json
KrakenD derives iss:     https://cognito-idp.ap-south-1.amazonaws.com/pool
Match ✅ — no issuer field needed
```

❌ **When you DO need `issuer` — Docker Compose + local IdP**

This is a Docker networking split problem. Your machine and KrakenD use different
hostnames to reach the same Keycloak container:

```
Postman (outside Docker):     calls http://localhost:8090
Keycloak stamps in token:     "iss": "http://localhost:8090/realms/trucare"

KrakenD (inside Docker):      calls http://keycloak:8090
KrakenD derives expected iss: "http://keycloak:8090/realms/trucare"

localhost ≠ keycloak → 401 Unauthorized
```

Fix — add explicit `issuer` to bridge the gap:

```json
"auth/validator": {
  "alg": "RS256",
  "jwk_url": "http://keycloak:8090/realms/trucare/protocol/openid-connect/certs",
  "issuer": "http://localhost:8090/realms/trucare",
  "cache": true,
  ...
}
```

Now KrakenD stops deriving and uses your explicit value:
```
expected iss → http://localhost:8090/realms/trucare  (from issuer field)
actual iss   → http://localhost:8090/realms/trucare  (from token)
Match ✅ → 200
```

---

## Debugging 401 on a valid token — the 3-step checklist

Whenever you get 401 from KrakenD on a freshly issued token, check these in order:

| Step | Check | How to verify |
|---|---|---|
| 1 | Token not expired | Paste at jwt.io → check `exp` claim is future timestamp |
| 2 | Signature valid | Paste at jwt.io → signature section shows green |
| 3 | Issuer matches | Compare `iss` claim in token vs base URL of `jwk_url` in krakend.json |

Issuer mismatch is the most common cause of 401 on a valid token.
Expiry is the second most common (token older than 1 hour by default in Keycloak).
Signature failure is rare — only happens if JWKS fetch failed or wrong `alg`.

---

## How to read a JWT without tools

A JWT is always 3 base64 segments separated by dots:

```
eyJhbGciOiJSUzI1NiJ9          ← Header  (base64)
.eyJzdWIiOiJVMDAxIn0           ← Payload (base64) ← read this
.SflKxwRJSMeKKF2QT4fw          ← Signature
```

Base64 is NOT encryption — it is reversible by anyone without a key.
The payload is deliberately readable. Only the signature proves authenticity.

Common base64 patterns you can recognise on sight:
```
eyJhbGci        → always starts a JWT header  {"alg"
aHR0cDovL2xvY2FsaG9zdA  → http://localhost
aHR0cHM6Ly9jb2duaXRv    → https://cognito
```

Paste any JWT at https://jwt.io to decode the payload instantly.
Always check the `iss` claim when debugging auth issues.

---

## Biggest Mistake at Step 4

**Forgetting `propagate_claims` after adding `auth/validator`.**

Without `propagate_claims`, KrakenD validates the JWT and blocks invalid tokens —
but it does NOT forward any claims to your backend.
Your interceptor sees no `X-User-*` headers → `UserContext.anonymous()` is set →
your audit logs show `userId=anonymous` for every authenticated request.

Always add `propagate_claims` alongside `auth/validator` on every protected endpoint.

**Second biggest mistake — missing `issuer` in local Docker setups.**

Symptoms: 401 on a token you just successfully fetched.
Cause: `iss` in token says `localhost:PORT`, KrakenD expects `container-name:PORT`.
Fix: Add `"issuer": "http://localhost:PORT/realms/..."` explicitly in krakend.json.
This only affects local development — remove it when deploying to production.

---

## Free Resources

| Resource | URL |
|---|---|
| KrakenD JWT validation docs | https://www.krakend.io/docs/authorization/jwt-validation/ |
| KrakenD claim propagation | https://www.krakend.io/docs/authorization/claims-propagation/ |
| AWS Cognito JWKS | https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html |
| JWT decoder | https://jwt.io |