# Keycloak Local Setup — Replacing AWS Cognito for Step 4

---

## What Keycloak Is

Keycloak is an open source Identity Provider (IdP) built by Red Hat.
It is the on-premise / self-hosted equivalent of AWS Cognito.

```
AWS Cognito (cloud)         Keycloak (local / self-hosted)
─────────────────────       ──────────────────────────────
User Pool                 = Realm
App Client                = Client
User attributes           = User attributes + Role mappers
JWKS endpoint             = /realms/{realm}/protocol/openid-connect/certs
Token endpoint            = /realms/{realm}/protocol/openid-connect/token
User management console   = Admin UI at http://localhost:8090
```

From KrakenD's point of view — only ONE thing changes between Cognito and Keycloak:

```
Cognito  jwk_url:
  https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_XXX/.well-known/jwks.json

Keycloak jwk_url:
  http://keycloak:8090/realms/trucare/protocol/openid-connect/certs
```

`alg`, `propagate_claims`, `roles` — all identical.
This is why Keycloak is the perfect local substitute.

---

## Pre-configured TrueCare Realm

The `keycloak/realm/trucare-realm.json` file sets up everything automatically:

### Realm
```
Name: trucare
Token lifetime: 1 hour
Algorithm: RS256
```

### Client (equivalent to Cognito App Client)
```
Client ID:     trucare-gateway
Client Secret: trucare-secret-local
Grant type:    Password (for local curl testing)
```

### Roles (equivalent to Cognito custom:role attribute)
```
DOCTOR  → Full patient and referral access
NURSE   → Limited access
ADMIN   → Full system access
PATIENT → Self-service only
```

### Pre-created Users
```
Username:  dr.mehta        Password: doctor123   Role: DOCTOR
Username:  nurse.priya     Password: nurse123    Role: NURSE
Username:  admin           Password: admin123    Role: ADMIN
```

---

## Starting Everything

```bash
# From the project root (where docker-compose.yml is)
docker-compose up --build

# Keycloak takes 60-90 seconds on first start (realm import)
# Watch for this log line before testing:
# "Listening on: http://0.0.0.0:8090"

# Or watch just Keycloak logs:
docker-compose logs -f keycloak
```

---

## Step 1 — Verify Keycloak is running

Open browser: http://localhost:8090

You should see the Keycloak welcome page.

Login to Admin Console:
```
URL:      http://localhost:8090/admin
Username: admin
Password: admin
```

In the top-left dropdown, switch from `master` to `trucare` realm.
You should see the pre-configured users, roles, and client.

---

## Step 2 — Get a JWT token (replaces Cognito hosted UI login)

Use the Keycloak token endpoint directly with curl.
This is equivalent to the Cognito InitiateAuth API call.

### Get a DOCTOR token
```bash
curl -s -X POST \
  http://localhost:8090/realms/trucare/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=trucare-gateway" \
  -d "client_secret=trucare-secret-local" \
  -d "username=dr.mehta" \
  -d "password=doctor123" \
  | jq '.access_token'
```

### Get a NURSE token
```bash
curl -s -X POST \
  http://localhost:8090/realms/trucare/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=trucare-gateway" \
  -d "client_secret=trucare-secret-local" \
  -d "username=nurse.priya" \
  -d "password=nurse123" \
  | jq '.access_token'
```

Save the token in a variable for convenience:
```bash
DOCTOR_TOKEN=$(curl -s -X POST \
  http://localhost:8090/realms/trucare/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=trucare-gateway" \
  -d "client_secret=trucare-secret-local" \
  -d "username=dr.mehta" \
  -d "password=doctor123" \
  | jq -r '.access_token')

echo $DOCTOR_TOKEN
```

---

## Step 3 — Inspect the JWT

Paste your token at https://jwt.io to see the decoded claims.

You should see the payload:
```json
{
  "sub": "some-uuid-keycloak-generated",
  "email": "dr.mehta@trucare.com",
  "name": "Raj Mehta",
  "role": "DOCTOR",
  "iss": "http://localhost:8090/realms/trucare",
  "exp": 1711968600,
  "iat": 1711965000
}
```

Note `"role": "DOCTOR"` — this is what KrakenD's `roles_key: "role"` matches against.

---

## Step 4 — Test KrakenD JWT validation

### Public endpoint (no token needed)
```bash
curl http://localhost:8080/api/v1/patients
# Expected: 200 with patient list
```

### Protected endpoint WITHOUT token
```bash
curl -v http://localhost:8080/api/v1/patients/P001
# Expected: 401 Unauthorized
```

### Protected endpoint WITH DOCTOR token
```bash
curl -H "Authorization: Bearer $DOCTOR_TOKEN" \
     http://localhost:8080/api/v1/patients/P001
# Expected: 200 with patient data
```

### NURSE trying to access DOCTOR-only endpoint
```bash
NURSE_TOKEN=$(curl -s -X POST \
  http://localhost:8090/realms/trucare/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=trucare-gateway" \
  -d "client_secret=trucare-secret-local" \
  -d "username=nurse.priya" \
  -d "password=nurse123" \
  | jq -r '.access_token')

curl -v -H "Authorization: Bearer $NURSE_TOKEN" \
     http://localhost:8080/api/v1/referrals
# Expected: 403 Forbidden (NURSE not in DOCTOR/ADMIN roles for this endpoint)
```

### Patient summary (aggregation + auth)
```bash
curl -H "Authorization: Bearer $DOCTOR_TOKEN" \
     http://localhost:8080/api/v1/patient-summary/P001
# Expected: 200 with merged patient + referrals
```

---

## Step 5 — Verify claim propagation in service logs

```bash
docker-compose logs -f patient-service
```

After a successful authenticated request you should see:
```
INFO  c.t.controller.PatientController - GET /patients/P001 accessed by userId=some-uuid role=DOCTOR
DEBUG c.t.interceptor.JwtClaimsInterceptor - UserContext set: userId=some-uuid, role=DOCTOR
DEBUG c.t.interceptor.JwtClaimsInterceptor - UserContext cleared after request completion
```

If you see `userId=anonymous` on a protected endpoint:
→ Check `propagate_claims` in `krakend.json`
→ Verify the claim name matches exactly (`role` not `custom:role` for Keycloak)

---

## Step 6 — Verify JWKS endpoint directly

KrakenD fetches public keys from this URL. Verify it works:

```bash
curl http://localhost:8090/realms/trucare/protocol/openid-connect/certs | jq .
```

Expected response:
```json
{
  "keys": [
    {
      "kid": "some-key-id",
      "kty": "RSA",
      "alg": "RS256",
      "use": "sig",
      "n": "0vx7agoebGcQ...",
      "e": "AQAB"
    }
  ]
}
```

This is identical in structure to Cognito's JWKS response.
KrakenD's `jwk_url` points here instead of Cognito.

---

## Cognito → Keycloak diff (what changes when moving to production)

```
LOCAL (Keycloak):
  "jwk_url": "http://keycloak:8090/realms/trucare/protocol/openid-connect/certs"
  "roles_key": "role"
  "disable_jwk_security": true   ← because Keycloak runs on plain HTTP locally

PRODUCTION (Cognito):
  "jwk_url": "https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_XXX/.well-known/jwks.json"
  "roles_key": "custom:role"     ← Cognito uses "custom:" prefix for custom attributes
  "disable_jwk_security": false  ← Cognito runs on HTTPS — keep TLS verification on
```

Everything else — `alg`, `propagate_claims` structure, `roles` values — stays identical.

---

## Validation Checklist

- [ ] `docker-compose up --build` completes without errors
- [ ] Keycloak Admin UI accessible at http://localhost:8090/admin
- [ ] TrueCare realm visible in the realm dropdown
- [ ] Users (dr.mehta, nurse.priya, admin) visible in Users section
- [ ] Token endpoint returns JWT for dr.mehta
- [ ] JWT decoded at jwt.io shows `"role": "DOCTOR"`
- [ ] `curl localhost:8080/api/v1/patients/P001` → 401 (no token)
- [ ] `curl -H "Authorization: Bearer $DOCTOR_TOKEN" localhost:8080/api/v1/patients/P001` → 200
- [ ] `curl -H "Authorization: Bearer $NURSE_TOKEN" localhost:8080/api/v1/referrals` → 403
- [ ] Service logs show `userId=<uuid> role=DOCTOR` for authenticated requests

---

## Troubleshooting

### KrakenD returns 401 even with valid token
Check KrakenD logs for:
```
KRAKEND ERROR: jwk_url fetch failed
```
→ Keycloak may still be starting. Wait for the health check to pass.

### KrakenD returns 401 with "token is expired"
Your token has a 1-hour lifetime. Get a fresh token using the curl command above.

### Service logs show userId=anonymous
→ `propagate_claims` is missing or claim names don't match.
→ Verify Keycloak token contains `"role"` claim (check at jwt.io).

### Keycloak realm not auto-imported
→ Volume mount may have failed. Try:
```bash
docker-compose down -v
docker-compose up --build
```
