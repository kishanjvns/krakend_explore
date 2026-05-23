#!/usr/bin/env bash
# ============================================================
#  Mediq — User Service API Test Script
#  Target : KrakenD gateway at localhost:8080
#  Auth   : Keycloak at localhost:8090
#
#  Prerequisites (run in separate terminals before this script):
#    kubectl port-forward -n mediq-dev svc/krakend          8080:8080
#    kubectl port-forward -n mediq-dev svc/keycloak-service 8090:8090
# ============================================================

set -euo pipefail

GATEWAY="http://localhost:8080/api/v1"
KEYCLOAK="http://localhost:8090"
REALM="mediq"
CLIENT="admin-cli"

# ── Colours ─────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# ── Counters ─────────────────────────────────────────────────
PASS=0
FAIL=0

# ── Helpers ──────────────────────────────────────────────────
pass() { echo -e "  ${GREEN}✔ PASS${RESET} — $1"; ((PASS++)); }
fail() { echo -e "  ${RED}✘ FAIL${RESET} — $1 (got: $2)"; ((FAIL++)); }
section() { echo -e "\n${CYAN}${BOLD}══ $1 ══${RESET}"; }
info()    { echo -e "  ${YELLOW}→${RESET} $1"; }

# Assert HTTP status code matches expected
assert_status() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label [HTTP $actual]"
  else
    fail "$label [expected HTTP $expected]" "HTTP $actual"
  fi
}

# ── Token fetch ───────────────────────────────────────────────
get_token() {
  local username="$1" password="$2"
  curl -s -X POST "$KEYCLOAK/realms/$REALM/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=$CLIENT&username=$username&password=$password" \
    | grep -o '"access_token":"[^"]*"' \
    | cut -d'"' -f4
}

# ── Connectivity check ────────────────────────────────────────
echo -e "\n${BOLD}Mediq User-Service API Tests${RESET}"
echo "Target: $GATEWAY"
echo "─────────────────────────────────────────"

info "Checking KrakenD connectivity..."
GW_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/../__health" 2>/dev/null || echo "000")
if [[ "$GW_STATUS" == "200" ]]; then
  pass "KrakenD reachable"
else
  echo -e "${RED}ERROR: KrakenD not reachable at localhost:8080 (status: $GW_STATUS)${RESET}"
  echo "Run: kubectl port-forward -n mediq-dev svc/krakend 8080:8080"
  exit 1
fi

info "Fetching tokens..."
ADMIN_TOKEN=$(get_token "admin"     "admin123")
DOCTOR_TOKEN=$(get_token "dr.mehta" "doctor123")

if [[ -z "$ADMIN_TOKEN" ]]; then
  echo -e "${RED}ERROR: Could not get admin token. Is Keycloak port-forwarded?${RESET}"
  echo "Run: kubectl port-forward -n mediq-dev svc/keycloak-service 8090:8090"
  exit 1
fi

pass "Admin token obtained"
pass "Doctor token obtained"

# ════════════════════════════════════════════════════════════
section "1. Patient Registration (POST /users/patients/register)"
# ════════════════════════════════════════════════════════════

TIMESTAMP=$(date +%s)
TEST_EMAIL="testpatient_${TIMESTAMP}@mediq.test"

# 1a — Positive: valid payload
info "1a. Valid registration payload"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/patients/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Test\",
    \"lastName\": \"Patient\",
    \"dateOfBirth\": \"1990-06-15\",
    \"password\": \"Test@1234\",
    \"contacts\": [{\"contactType\": \"EMAIL\", \"contactValue\": \"$TEST_EMAIL\", \"isPrimary\": true}],
    \"addresses\": []
  }")
assert_status "Valid patient registration" "201" "$STATUS"

# Save the registered patient's email for later tests
PATIENT_EMAIL="$TEST_EMAIL"

# 1b — Negative: missing dateOfBirth (required @NotNull)
info "1b. Missing dateOfBirth → expect 400"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/patients/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Test\",
    \"lastName\": \"NoDate\",
    \"password\": \"Test@1234\",
    \"contacts\": [{\"contactType\": \"EMAIL\", \"contactValue\": \"nodate@mediq.test\", \"isPrimary\": true}],
    \"addresses\": []
  }")
assert_status "Missing dateOfBirth → 400" "400" "$STATUS"

# 1c — Negative: missing firstName (required @NotBlank)
info "1c. Missing firstName → expect 400"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/patients/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"lastName\": \"Only\",
    \"dateOfBirth\": \"1990-01-01\",
    \"password\": \"Test@1234\",
    \"contacts\": [{\"contactType\": \"EMAIL\", \"contactValue\": \"nofirst@mediq.test\", \"isPrimary\": true}],
    \"addresses\": []
  }")
assert_status "Missing firstName → 400" "400" "$STATUS"

# 1d — Negative: empty contacts list (required @NotEmpty)
info "1d. Empty contacts → expect 400"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/patients/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Test\",
    \"lastName\": \"NoContact\",
    \"dateOfBirth\": \"1990-01-01\",
    \"password\": \"Test@1234\",
    \"contacts\": [],
    \"addresses\": []
  }")
assert_status "Empty contacts → 400" "400" "$STATUS"

# 1e — Negative: completely empty body
info "1e. Empty body → expect 400"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/patients/register" \
  -H "Content-Type: application/json" \
  -d "{}")
assert_status "Empty body → 400" "400" "$STATUS"

# ════════════════════════════════════════════════════════════
section "2. Doctor Registration (POST /users/doctors/register)"
# ════════════════════════════════════════════════════════════

DOCTOR_EMAIL="testdoctor_${TIMESTAMP}@mediq.test"

# 2a — Positive: valid payload
info "2a. Valid doctor registration"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/doctors/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Test\",
    \"lastName\": \"Doctor\",
    \"dateOfBirth\": \"1980-03-20\",
    \"password\": \"Test@1234\",
    \"licenseNumber\": \"MCI-TEST-${TIMESTAMP}\",
    \"licenseExpiry\": \"2030-12-31\",
    \"yearsOfExperience\": 10,
    \"contacts\": [{\"contactType\": \"EMAIL\", \"contactValue\": \"$DOCTOR_EMAIL\", \"isPrimary\": true}],
    \"addresses\": []
  }")
assert_status "Valid doctor registration" "201" "$STATUS"

# 2b — Negative: missing licenseExpiry
info "2b. Missing licenseExpiry → expect 400"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/doctors/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Test\",
    \"lastName\": \"NoExpiry\",
    \"dateOfBirth\": \"1980-01-01\",
    \"password\": \"Test@1234\",
    \"licenseNumber\": \"MCI-NO-EXPIRY\",
    \"yearsOfExperience\": 5,
    \"contacts\": [{\"contactType\": \"EMAIL\", \"contactValue\": \"noexpiry@mediq.test\", \"isPrimary\": true}],
    \"addresses\": []
  }")
assert_status "Missing licenseExpiry → 400" "400" "$STATUS"

# ════════════════════════════════════════════════════════════
section "3. GET /users/me (requires any valid token)"
# ════════════════════════════════════════════════════════════

# 3a — Positive: with admin token
info "3a. GET /users/me with valid admin token"
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY/users/me" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -1)
assert_status "GET /users/me with valid token" "200" "$STATUS"

# 3b — Negative: no token
info "3b. GET /users/me with no token → expect 401"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/me")
assert_status "GET /users/me without token → 401" "401" "$STATUS"

# 3c — Negative: invalid/garbage token
info "3c. GET /users/me with invalid token → expect 401"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/me" \
  -H "Authorization: Bearer this.is.not.a.real.token")
assert_status "GET /users/me with bad token → 401" "401" "$STATUS"

# ════════════════════════════════════════════════════════════
section "4. GET /users/{userId} (requires any valid token)"
# ════════════════════════════════════════════════════════════

# Fetch the admin user's ID from /users/me
ADMIN_USER_ID=$(curl -s "$GATEWAY/users/me" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
info "Admin userId resolved: $ADMIN_USER_ID"

# 4a — Positive: valid userId + valid token
info "4a. GET /users/{userId} — valid ID + valid token"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/$ADMIN_USER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_status "GET /users/{validId} → 200" "200" "$STATUS"

# 4b — Negative: non-existent UUID
info "4b. GET /users/{userId} — non-existent ID → expect 404"
FAKE_ID="00000000-0000-0000-0000-000000000000"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/$FAKE_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_status "GET /users/{nonExistentId} → 404" "404" "$STATUS"

# 4c — Negative: no token
info "4c. GET /users/{userId} — no token → expect 401"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/$ADMIN_USER_ID")
assert_status "GET /users/{userId} without token → 401" "401" "$STATUS"

# ════════════════════════════════════════════════════════════
section "5. GET /users/doctors/pending-verification (ADMIN/DOCTOR only)"
# ════════════════════════════════════════════════════════════

# 5a — Positive: admin token
info "5a. Pending verification with admin token → expect 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/doctors/pending-verification" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_status "Pending verification (admin) → 200" "200" "$STATUS"

# 5b — Positive: doctor token
info "5b. Pending verification with doctor token → expect 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/doctors/pending-verification" \
  -H "Authorization: Bearer $DOCTOR_TOKEN")
assert_status "Pending verification (doctor) → 200" "200" "$STATUS"

# 5c — Negative: no token
info "5c. Pending verification without token → expect 401"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/users/doctors/pending-verification")
assert_status "Pending verification (no token) → 401" "401" "$STATUS"

# ════════════════════════════════════════════════════════════
section "6. PUT /users/doctors/{id}/verify (ADMIN/DOCTOR only)"
# ════════════════════════════════════════════════════════════

FAKE_DOCTOR_ID="00000000-0000-0000-0000-000000000001"

# 6a — Negative: non-existent doctor ID → 404
info "6a. Verify non-existent doctor → expect 404"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$GATEWAY/users/doctors/$FAKE_DOCTOR_ID/verify" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
assert_status "Verify non-existent doctor → 404" "404" "$STATUS"

# 6b — Negative: no token → 401
info "6b. Verify doctor without token → expect 401"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$GATEWAY/users/doctors/$FAKE_DOCTOR_ID/verify" \
  -H "Content-Type: application/json" \
  -d '{}')
assert_status "Verify doctor (no token) → 401" "401" "$STATUS"

# ════════════════════════════════════════════════════════════
section "7. OTP Flow (POST /users/{userId}/send-otp)"
# ════════════════════════════════════════════════════════════

# 7a — Positive: valid userId (admin user)
info "7a. Send OTP to valid user"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/$ADMIN_USER_ID/send-otp" \
  -H "Content-Type: application/json")
assert_status "Send OTP (valid userId) → 200" "200" "$STATUS"

# 7b — Negative: non-existent userId → 404
info "7b. Send OTP to non-existent user → expect 404"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/users/$FAKE_ID/send-otp" \
  -H "Content-Type: application/json")
assert_status "Send OTP (non-existent userId) → 404" "404" "$STATUS"

# 7c — Negative: wrong OTP → verify-otp should reject
info "7c. Verify OTP with wrong code → expect 200 (with error in body)"
RESP=$(curl -s -X POST "$GATEWAY/users/$ADMIN_USER_ID/verify-otp" \
  -H "Content-Type: application/json" \
  -d '{"otp": "000000"}')
if echo "$RESP" | grep -q "wrong\|invalid\|Invalid\|Wrong\|expired\|Expired"; then
  pass "Wrong OTP returns error message in body"
else
  fail "Wrong OTP response unexpected" "$RESP"
fi

# ════════════════════════════════════════════════════════════
section "8. Admin Roles (GET /admin/roles)"
# ════════════════════════════════════════════════════════════

# 8a — Positive: admin token
info "8a. List roles with admin token → expect 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/admin/roles" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_status "List roles (admin) → 200" "200" "$STATUS"

# 8b — Negative: no token → 401
info "8b. List roles without token → expect 401"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/admin/roles")
assert_status "List roles (no token) → 401" "401" "$STATUS"

# ════════════════════════════════════════════════════════════
#  Summary
# ════════════════════════════════════════════════════════════
TOTAL=$((PASS + FAIL))
echo -e "\n${BOLD}─────────────────────────────────────────${RESET}"
echo -e "${BOLD}Results: ${GREEN}$PASS passed${RESET} / ${RED}$FAIL failed${RESET} / $TOTAL total"
echo -e "${BOLD}─────────────────────────────────────────${RESET}"

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
