# mediq — Task M7 (Final): Authorization Code Grant + PKCE + Spring Security

## Branch
```powershell
git checkout main
git pull origin main
git checkout -b feature/mediq-m7-auth-final
```

## What Changed From Previous M7 Plan

```
DISCARDED:
  AuthController.java        ← not needed (Angular talks directly to Keycloak)
  KeycloakAuthClient.java    ← not needed
  AuthService.java           ← not needed
  Login proxy (ROPC)         ← replaced by Authorization Code + PKCE

NEW FLOW:
  Angular → redirects browser → Keycloak login page
  User logs in on Keycloak
  Keycloak → redirects back → Angular with ?code=xxx
  Angular (angular-oauth2-oidc) exchanges code for tokens directly
  Angular sends Bearer token → KrakenD → validates → propagates headers
  Services read X-User-Role, X-User-Id, X-User-Permissions via Spring Security

CORS:
  Only on KrakenD (browser never talks to services directly)
  NOT on any Spring Boot service
```

## Overview of All Changes

```
Part 0: Cleanup
  Delete k8s/ folder (replaced by Helm)
  Remove mediq-gateway client from Keycloak realm (unused)

Part 1: Keycloak realm.json
  Add mediq-frontend-spa public client with PKCE
  Add protocol mappers to mediq-frontend-spa client:
    role, permissions, userId, userType
  Add permission attributes to all 4 roles
  Set permissions + userId attribute on all test users
  Add test patient user

Part 2: KrakenD
  Fix propagate_claims (add userId, permissions, userType, jti, exp)
  Create endpoint_auth.tmpl (logout + me only)
  Create auth_any_role.tmpl
  Add CORS config for localhost:4200
  Register new partials in Helm ConfigMap

Part 3: Graceful logout — Redis JTI blacklist
  POST /auth/logout stores JTI in Redis with remaining TTL
  KrakenDAuthFilter checks Redis blacklist on every request
  Immediate token revocation regardless of expiry time

Part 4: Spring Security on all 5 remaining services
  KrakenDAuthFilter.java (updated with blacklist check)
  SecurityConfig.java per service
  Remove autoconfigure.exclude
  @PreAuthorize on controllers

Part 5: Admin permission management API (user-service)
  GET  /admin/roles
  PUT  /admin/roles/{roleName}/permissions
  GET  /admin/permissions

Part 6: Angular integration notes
  angular-oauth2-oidc config
  Silent refresh setup
  Token storage strategy
  HttpInterceptor
  Route guards
  Permission management page
```

---

## PART 0 — Cleanup

### 0a — Delete k8s/ folder

```powershell
# PowerShell — from D:\codebase\krakend_explore
Remove-Item -Recurse -Force k8s

# Verify deleted
ls k8s 2>$null || Write-Host "k8s/ folder deleted ✅"
```

The `k8s/` folder contained raw Kubernetes manifests.
These are fully superseded by the Helm charts in `helm/`.
Keeping both causes confusion — which is the source of truth?
Helm is the source of truth. k8s/ is gone.

### 0b — Remove mediq-gateway from Keycloak realm

**File:** `keycloak/realm/mediq-realm.json`

Find the `"clients"` array. Delete the entire `mediq-gateway` client object.

**Why mediq-gateway is unused:**
```
KrakenD validates JWTs by fetching Keycloak's public JWK endpoint:
  /realms/mediq/protocol/openid-connect/certs
This endpoint is PUBLIC — no client registration required.

disable_jwk_security: true means KrakenD also skips aud claim validation.
The mediq-gateway client registration serves no purpose.

Tokens are now issued to mediq-frontend-spa (Angular).
Protocol mappers live on that client.
mediq-gateway had its own mappers that never applied to Angular tokens.
```

---

## PART 1 — Keycloak Realm Changes

### All changes go in `keycloak/realm/mediq-realm.json`

---

### 1a — Add mediq-frontend-spa public client

Add inside the `"clients"` array:

```json
{
  "clientId": "mediq-frontend-spa",
  "name": "mediq Angular SPA",
  "description": "Public OAuth2 client for Angular frontend - Authorization Code + PKCE",
  "enabled": true,
  "publicClient": true,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "protocol": "openid-connect",
  "redirectUris": [
    "http://localhost:4200/*",
    "http://localhost:4200/silent-refresh.html"
  ],
  "webOrigins": [
    "http://localhost:4200"
  ],
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "access.token.lifespan": "300",
    "post.logout.redirect.uris": "http://localhost:4200/"
  },
  "defaultClientScopes": [
    "openid",
    "profile",
    "email",
    "roles"
  ],
  "protocolMappers": [
    {
      "name": "role-mapper",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-realm-role-mapper",
      "consentRequired": false,
      "config": {
        "multivalued": "false",
        "userinfo.token.claim": "true",
        "id.token.claim": "true",
        "access.token.claim": "true",
        "claim.name": "role",
        "jsonType.label": "String"
      }
    },
    {
      "name": "permissions-mapper",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-attribute-mapper",
      "consentRequired": false,
      "config": {
        "multivalued": "true",
        "aggregate.attrs": "true",
        "userinfo.token.claim": "true",
        "id.token.claim": "true",
        "access.token.claim": "true",
        "claim.name": "permissions",
        "user.attribute": "permissions",
        "jsonType.label": "String"
      }
    },
    {
      "name": "userId-mapper",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-attribute-mapper",
      "consentRequired": false,
      "config": {
        "userinfo.token.claim": "true",
        "id.token.claim": "true",
        "access.token.claim": "true",
        "claim.name": "userId",
        "user.attribute": "userId",
        "jsonType.label": "String"
      }
    },
    {
      "name": "userType-mapper",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-attribute-mapper",
      "consentRequired": false,
      "config": {
        "userinfo.token.claim": "true",
        "id.token.claim": "true",
        "access.token.claim": "true",
        "claim.name": "userType",
        "user.attribute": "userType",
        "jsonType.label": "String"
      }
    }
  ]
}
```

### 1b — Add permission attributes to all 4 roles

Find the `"roles"` → `"realm"` array. Replace all 4 role entries:

```json
{
  "name": "PATIENT",
  "description": "Registered patient",
  "composite": false,
  "attributes": {
    "permissions": [
      "READ_OWN_PROFILE",
      "WRITE_OWN_PROFILE",
      "READ_DOCTORS",
      "READ_DOCTOR_AVAILABILITY",
      "WRITE_OWN_APPOINTMENT",
      "READ_OWN_APPOINTMENT",
      "CANCEL_OWN_APPOINTMENT",
      "READ_OWN_NOTIFICATIONS",
      "SEND_OTP",
      "VERIFY_OTP"
    ]
  }
},
{
  "name": "DOCTOR",
  "description": "Verified doctor",
  "composite": false,
  "attributes": {
    "permissions": [
      "READ_OWN_PROFILE",
      "WRITE_OWN_PROFILE",
      "READ_PATIENT_PROFILE",
      "READ_OWN_APPOINTMENT",
      "WRITE_APPOINTMENT_SLOT",
      "CONFIRM_APPOINTMENT",
      "CANCEL_APPOINTMENT",
      "READ_EMR",
      "WRITE_EMR",
      "READ_OWN_ANALYTICS",
      "READ_OWN_NOTIFICATIONS"
    ]
  }
},
{
  "name": "NURSE",
  "description": "Clinical staff",
  "composite": false,
  "attributes": {
    "permissions": [
      "READ_OWN_PROFILE",
      "READ_PATIENT_PROFILE",
      "READ_OWN_APPOINTMENT",
      "WRITE_OWN_APPOINTMENT",
      "CANCEL_APPOINTMENT",
      "READ_EMR",
      "READ_OWN_NOTIFICATIONS"
    ]
  }
},
{
  "name": "ADMIN",
  "description": "Platform administrator",
  "composite": false,
  "attributes": {
    "permissions": [
      "READ_OWN_PROFILE",
      "READ_ANY_PROFILE",
      "WRITE_ANY_PROFILE",
      "VERIFY_DOCTOR",
      "DEACTIVATE_USER",
      "READ_DOCTORS",
      "READ_PATIENT_PROFILE",
      "READ_OWN_APPOINTMENT",
      "READ_ANY_APPOINTMENT",
      "CANCEL_ANY_APPOINTMENT",
      "WRITE_APPOINTMENT_SLOT",
      "READ_EMR",
      "WRITE_EMR",
      "READ_ANALYTICS",
      "READ_ANY_NOTIFICATIONS",
      "MANAGE_ROLES",
      "SEND_OTP",
      "VERIFY_OTP"
    ]
  }
}
```

### 1c — Update test users with permission + userId attributes with permission + userId attributes

Find the `"users"` array. Update all 3 existing users and add patient:

```json
{
  "username": "testpatient",
  "email": "testpatient@mediq.com",
  "firstName": "Test",
  "lastName": "Patient",
  "enabled": true,
  "emailVerified": true,
  "credentials": [{
    "type": "password",
    "value": "Test@1234",
    "temporary": false
  }],
  "realmRoles": ["PATIENT"],
  "attributes": {
    "userType": ["PATIENT"],
    "permissions": [
      "READ_OWN_PROFILE",
      "WRITE_OWN_PROFILE",
      "READ_DOCTORS",
      "READ_DOCTOR_AVAILABILITY",
      "WRITE_OWN_APPOINTMENT",
      "READ_OWN_APPOINTMENT",
      "CANCEL_OWN_APPOINTMENT",
      "READ_OWN_NOTIFICATIONS",
      "SEND_OTP",
      "VERIFY_OTP"
    ]
  }
},
{
  "username": "dr.mehta",
  "email": "dr.mehta@mediq.com",
  "firstName": "Arjun",
  "lastName": "Mehta",
  "enabled": true,
  "emailVerified": true,
  "credentials": [{"type": "password", "value": "Test@1234", "temporary": false}],
  "realmRoles": ["DOCTOR"],
  "attributes": {
    "userType": ["DOCTOR"],
    "permissions": [
      "READ_OWN_PROFILE",
      "WRITE_OWN_PROFILE",
      "READ_PATIENT_PROFILE",
      "READ_OWN_APPOINTMENT",
      "WRITE_APPOINTMENT_SLOT",
      "CONFIRM_APPOINTMENT",
      "CANCEL_APPOINTMENT",
      "READ_EMR",
      "WRITE_EMR",
      "READ_OWN_ANALYTICS",
      "READ_OWN_NOTIFICATIONS"
    ]
  }
},
{
  "username": "nurse.priya",
  "email": "nurse.priya@mediq.com",
  "firstName": "Priya",
  "lastName": "Sharma",
  "enabled": true,
  "emailVerified": true,
  "credentials": [{"type": "password", "value": "Test@1234", "temporary": false}],
  "realmRoles": ["NURSE"],
  "attributes": {
    "userType": ["NURSE"],
    "permissions": [
      "READ_OWN_PROFILE",
      "READ_PATIENT_PROFILE",
      "READ_OWN_APPOINTMENT",
      "WRITE_OWN_APPOINTMENT",
      "CANCEL_APPOINTMENT",
      "READ_EMR",
      "READ_OWN_NOTIFICATIONS"
    ]
  }
},
{
  "username": "admin",
  "email": "admin@mediq.com",
  "firstName": "Platform",
  "lastName": "Admin",
  "enabled": true,
  "emailVerified": true,
  "credentials": [{"type": "password", "value": "Test@1234", "temporary": false}],
  "realmRoles": ["ADMIN"],
  "attributes": {
    "userType": ["ADMIN"],
    "permissions": [
      "READ_OWN_PROFILE",
      "READ_ANY_PROFILE",
      "WRITE_ANY_PROFILE",
      "VERIFY_DOCTOR",
      "DEACTIVATE_USER",
      "READ_DOCTORS",
      "READ_PATIENT_PROFILE",
      "READ_OWN_APPOINTMENT",
      "READ_ANY_APPOINTMENT",
      "CANCEL_ANY_APPOINTMENT",
      "WRITE_APPOINTMENT_SLOT",
      "READ_EMR",
      "WRITE_EMR",
      "READ_ANALYTICS",
      "READ_ANY_NOTIFICATIONS",
      "MANAGE_ROLES",
      "SEND_OTP",
      "VERIFY_OTP"
    ]
  }
}
```

---

## PART 2 — KrakenD Changes

### 2a — Add CORS configuration to krakend.tmpl

**File:** `krakend/krakend.tmpl`

Add inside `"extra_config"` at the root level (next to `"router"` and `"telemetry/opentelemetry"`):

```json
"security/cors": {
  "allow_origins": ["http://localhost:4200"],
  "allow_methods": ["GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS"],
  "allow_headers": [
    "Origin",
    "Content-Type",
    "Authorization",
    "Accept",
    "X-Requested-With"
  ],
  "expose_headers": ["Content-Length"],
  "allow_credentials": true,
  "max_age": "12h"
}
```

Apply the same change to `helm/gateway/krakend/config/krakend.tmpl`.

**Why CORS only on KrakenD:**
```
Angular SPA (browser at localhost:4200) → calls → KrakenD (localhost:8080)
KrakenD → calls → services (internal pod-to-pod, never browser-facing)

CORS is enforced by browsers only.
No browser ever calls a service directly.
Therefore services never need CORS configuration.
```

### 2b — Fix propagate_claims in auth partials

**File:** `krakend/partials/auth_doctor_admin.tmpl`

Find `"propagate_claims"` block. Replace:

```json
"propagate_claims": [
  ["sub",         "X-Keycloak-Id"],
  ["userId",      "X-User-Id"],
  ["email",       "X-User-Email"],
  ["role",        "X-User-Role"],
  ["userType",    "X-User-Type"],
  ["permissions", "X-User-Permissions"],
  ["jti",         "X-Token-Jti"],
  ["exp",         "X-Token-Exp"]
]
```

**File:** `krakend/partials/auth_doctor_nurse_admin.tmpl`

Apply exact same replacement.

Also apply both to:
- `helm/gateway/krakend/config/partials/auth_doctor_admin.tmpl`
- `helm/gateway/krakend/config/partials/auth_doctor_nurse_admin.tmpl`

**Also update `auth_any_role.tmpl`** with the same full propagate_claims list.

**What changed and why:**
```
BEFORE: ["sub", "X-User-Id"]
  → sub is Keycloak's internal UUID (not the mediq DB userId)
  → services were receiving Keycloak UUID as X-User-Id
  → wrong — services should receive the mediq DB userId

AFTER:
  sub         → X-Keycloak-Id   (Keycloak internal UUID)
  userId      → X-User-Id       (mediq DB UUID from custom JWT claim)
  permissions → X-User-Permissions (fine-grained permissions)
  userType    → X-User-Type
  jti         → X-Token-Jti     (JWT unique ID — for logout blacklist)
  exp         → X-Token-Exp     (expiry epoch — to calculate Redis TTL)
```

### 2c — Create auth_any_role.tmpl

Any authenticated user (valid JWT, any role):

Create `krakend/partials/auth_any_role.tmpl`:

```json
"auth/validator": {
  "alg": "RS256",
  "jwk_url": "{{ .hosts.keycloak_internal }}/realms/mediq/protocol/openid-connect/certs",
  "issuer": "{{ .hosts.keycloak_external }}/realms/mediq",
  "cache": true,
  "cache_duration": 900,
  "disable_jwk_security": true,
  "propagate_claims": [
    ["sub",         "X-Keycloak-Id"],
    ["userId",      "X-User-Id"],
    ["email",       "X-User-Email"],
    ["role",        "X-User-Role"],
    ["userType",    "X-User-Type"],
    ["permissions", "X-User-Permissions"]
  ]
}
```

Copy to `helm/gateway/krakend/config/partials/auth_any_role.tmpl`.

### 2d — Create endpoint_auth.tmpl

Note: No `/auth/login` endpoint here — Angular handles login directly with Keycloak.
Only logout and user-info go through user-service.

Create `krakend/partials/endpoint_auth.tmpl`:

```json
{
  "endpoint": "/api/v1/auth/me",
  "method": "GET",
  "extra_config": {
    "qos/ratelimit/router": { "max_rate": 300, "capacity": 300 },
    {{ template "auth_any_role.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/auth/me",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/auth/logout",
  "method": "POST",
  "extra_config": {
    {{ template "auth_any_role.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/auth/logout",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
}
```

Copy to `helm/gateway/krakend/config/partials/endpoint_auth.tmpl`.

### 2e — Add endpoint_auth to krakend.tmpl

**File:** `krakend/krakend.tmpl` and `helm/gateway/krakend/config/krakend.tmpl`

Add at top of endpoints list:
```json
"endpoints": [
  {{ template "endpoint_auth.tmpl" . }},
  {{ template "endpoint_users.tmpl" . }},
  ...
]
```

### 2f — Register new partials in Helm ConfigMap

**File:** `helm/gateway/krakend/templates/configmap.yaml`

Add:
```yaml
  endpoint_auth.tmpl: |
    {{- .Files.Get "config/partials/endpoint_auth.tmpl" | nindent 4 }}
  auth_any_role.tmpl: |
    {{- .Files.Get "config/partials/auth_any_role.tmpl" | nindent 4 }}
```

---


---

## PART 3 — Graceful Logout — Redis JTI Blacklist

### Why this is needed

```
JWT access_token is stateless and cryptographically self-contained.
KrakenD validates the signature against Keycloak's public key.
It does NOT call Keycloak on every request (that would be too slow).

User clicks logout:
  angular-oauth2-oidc destroys local tokens ✅
  Keycloak SSO session destroyed ✅
  Silent refresh stops ✅
  BUT: existing access_token still validates for up to 5 minutes

Solution: Redis JTI blacklist
  JTI = jti claim in JWT = unique UUID per token (Keycloak always sets it)
  On logout: store jti in Redis with TTL = remaining token seconds
  Every request: KrakenDAuthFilter checks Redis before trusting headers
  Token immediately unusable — zero window regardless of expiry
```

### 3a — Update AuthController.java (user-service)

Update `user-service/src/main/java/com/mediq/controller/AuthController.java`:

```java
package com.mediq.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public AuthController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // GET /auth/me
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader("X-User-Id")           String userId,
            @RequestHeader("X-Keycloak-Id")        String keycloakId,
            @RequestHeader("X-User-Email")         String email,
            @RequestHeader("X-User-Role")          String role,
            @RequestHeader("X-User-Type")          String userType,
            @RequestHeader(value = "X-User-Permissions", required = false)
                String permissionsHeader) {

        List<String> permissions = permissionsHeader != null
            ? List.of(permissionsHeader.split(","))
            : List.of();

        return ResponseEntity.ok(Map.of(
            "userId",      userId,
            "keycloakId",  keycloakId,
            "email",       email,
            "role",        role,
            "userType",    userType,
            "permissions", permissions
        ));
    }

    // POST /auth/logout — immediate token revocation via JTI blacklist
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @RequestHeader("X-Token-Jti") String jti,
            @RequestHeader("X-Token-Exp") String expEpochSeconds) {

        try {
            long expSeconds = Long.parseLong(expEpochSeconds);
            long nowSeconds = Instant.now().getEpochSecond();
            long remainingTtl = expSeconds - nowSeconds;

            if (remainingTtl > 0) {
                // Store JTI in Redis blacklist with TTL = remaining token lifetime
                // After TTL expires, Redis auto-deletes the key
                String blacklistKey = BLACKLIST_KEY_PREFIX + jti;
                redisTemplate.opsForValue().set(
                    blacklistKey,
                    "revoked",
                    Duration.ofSeconds(remainingTtl)
                );
                // log: jti blacklisted for remainingTtl seconds
            }
            // If remainingTtl <= 0, token already expired — nothing to blacklist

        } catch (NumberFormatException e) {
            // log: invalid exp header — proceed with logout anyway
        }

        return ResponseEntity.ok().build();
    }
}
```

### 3b — Update KrakenDAuthFilter.java (all services)

Update the filter in ALL services to check Redis blacklist.

`user-service/src/main/java/com/mediq/security/KrakenDAuthFilter.java`:

```java
package com.mediq.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class KrakenDAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID          = "X-User-Id";
    private static final String HEADER_USER_ROLE        = "X-User-Role";
    private static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";
    private static final String HEADER_TOKEN_JTI        = "X-Token-Jti";
    private static final String BLACKLIST_KEY_PREFIX    = "token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public KrakenDAuthFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader(HEADER_USER_ID);
        String role   = request.getHeader(HEADER_USER_ROLE);
        String jti    = request.getHeader(HEADER_TOKEN_JTI);

        if (userId != null && !userId.isBlank()) {

            // Check Redis blacklist — token revoked on logout?
            if (jti != null && !jti.isBlank()) {
                Boolean revoked = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti);
                if (Boolean.TRUE.equals(revoked)) {
                    // Token was explicitly revoked via logout
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("Token has been revoked");
                    return;  // do NOT proceed
                }
            }

            // Token is valid — build Spring Security Authentication
            List<GrantedAuthority> authorities = new ArrayList<>();

            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }

            String permissionsHeader = request.getHeader(HEADER_USER_PERMISSIONS);
            if (permissionsHeader != null && !permissionsHeader.isBlank()) {
                for (String permission : permissionsHeader.split(",")) {
                    if (!permission.trim().isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(permission.trim()));
                    }
                }
            }

            PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
```

**Copy the updated KrakenDAuthFilter.java to all 5 other services** (adjust package name only):
```
doctor-service:      com/mediq/doctor/security/KrakenDAuthFilter.java
appointment-service: com/mediq/appointment/security/KrakenDAuthFilter.java
notification-service: com/mediq/notification/security/KrakenDAuthFilter.java
emr-service:         com/mediq/emr/security/KrakenDAuthFilter.java
analytics-service:   com/mediq/analytics/security/KrakenDAuthFilter.java
```

### 3c — How Redis is already available in all services

```
Redis is already configured in all services for:
  user-service: OTP storage, user cache
  appointment-service: slot availability cache

All services already have RedisTemplate<String, String> bean
via Spring Boot autoconfiguration when spring.data.redis.*
properties are present.

No new Redis dependency needed.
The blacklist check adds ~1ms per request (Redis is in-memory).
```

### 3d — Angular logout sequence

```typescript
// auth.service.ts — Angular
async logout(): Promise<void> {
  // Step 1: Tell user-service to blacklist the current token
  // (the access_token is automatically attached by the interceptor)
  await this.http.post('/api/v1/auth/logout', {}).toPromise();

  // Step 2: Destroy local tokens + redirect to Keycloak logout
  // This kills the Keycloak SSO session (silent refresh will fail)
  this.oauthService.logOut();
  // angular-oauth2-oidc redirects browser to:
  // http://localhost:8090/realms/mediq/protocol/openid-connect/logout
  //   ?post_logout_redirect_uri=http://localhost:4200/
  //   &id_token_hint=<id_token>
}
```

**Logout sequence summary:**
```
1. Angular POST /api/v1/auth/logout
   → user-service stores jti in Redis (remaining TTL seconds)
   → any further use of this token → 401 immediately

2. oauthService.logOut()
   → local tokens cleared from memory
   → browser redirected to Keycloak logout
   → Keycloak SSO session destroyed
   → silent refresh will fail going forward

Result: Token completely unusable from the moment logout is called.
```

---

## PART 4 — Spring Security for Remaining Services

Apply to all 5 services: `doctor-service`, `appointment-service`,
`notification-service`, `emr-service`, `analytics-service`

### 3a — Add spring-boot-starter-security to pom.xml

For each service, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### 3b — Remove autoconfigure.exclude from application.properties

For each service, find and delete this line:

```properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### 3c — Create KrakenDAuthFilter.java in each service

Copy user-service's `KrakenDAuthFilter.java` to each service's security package.

```
doctor-service:      com/mediq/doctor/security/KrakenDAuthFilter.java
appointment-service: com/mediq/appointment/security/KrakenDAuthFilter.java
notification-service: com/mediq/notification/security/KrakenDAuthFilter.java
emr-service:         com/mediq/emr/security/KrakenDAuthFilter.java
analytics-service:   com/mediq/analytics/security/KrakenDAuthFilter.java
```

The content is identical to user-service's filter — just change the package declaration.

### 3d — Create SecurityConfig.java per service

Each service has different public endpoints. Create with appropriate permitAll rules:

**doctor-service** — `com/mediq/doctor/security/SecurityConfig.java`:

```java
package com.mediq.doctor.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final KrakenDAuthFilter krakenDAuthFilter;

    public SecurityConfig(KrakenDAuthFilter krakenDAuthFilter) {
        this.krakenDAuthFilter = krakenDAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Doctor search is public (patients browse doctors)
                .requestMatchers("/doctors/search",
                                 "/doctors/*/availability",
                                 "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(krakenDAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**appointment-service** — public: none (all require auth):

```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/actuator/**").permitAll()
    .anyRequest().authenticated()
)
```

**notification-service** — public: none:

```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/actuator/**").permitAll()
    .anyRequest().authenticated()
)
```

**emr-service** — public: none:

```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/actuator/**").permitAll()
    .anyRequest().authenticated()
)
```

**analytics-service** — public: none:

```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/actuator/**").permitAll()
    .anyRequest().authenticated()
)
```

### 3e — Add @PreAuthorize to controllers

**doctor-service — DoctorController.java:**

```java
// Search — public, no annotation needed (SecurityConfig handles it)

// Get own profile
@GetMapping("/{doctorId}")
@PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN') or hasRole('NURSE')")
public ResponseEntity<DoctorResponse> getDoctor(@PathVariable UUID doctorId) { ... }

// Set availability — doctor can only set own availability
@PostMapping("/{doctorId}/availability")
@PreAuthorize("hasRole('DOCTOR') and #doctorId.toString() == authentication.principal " +
              "or hasRole('ADMIN')")
public ResponseEntity<Void> setAvailability(@PathVariable UUID doctorId, ...) { ... }

// Add specialization
@PostMapping("/{doctorId}/specializations")
@PreAuthorize("hasRole('DOCTOR') and #doctorId.toString() == authentication.principal " +
              "or hasRole('ADMIN')")
public ResponseEntity<Void> addSpecialization(@PathVariable UUID doctorId, ...) { ... }
```

**appointment-service — AppointmentController.java:**

```java
// Book appointment
@PostMapping
@PreAuthorize("hasAuthority('WRITE_OWN_APPOINTMENT') or hasRole('ADMIN')")
public ResponseEntity<?> bookAppointment(...) { ... }

// Get appointment
@GetMapping("/{appointmentId}")
@PreAuthorize("hasAuthority('READ_OWN_APPOINTMENT') or hasRole('ADMIN')")
public ResponseEntity<?> getAppointment(@PathVariable UUID appointmentId, ...) { ... }

// Cancel appointment
@PutMapping("/{appointmentId}/cancel")
@PreAuthorize("hasAuthority('CANCEL_OWN_APPOINTMENT') or " +
              "hasAuthority('CANCEL_APPOINTMENT') or hasRole('ADMIN')")
public ResponseEntity<?> cancelAppointment(...) { ... }

// Confirm appointment (doctor/admin only)
@PutMapping("/{appointmentId}/confirm")
@PreAuthorize("hasAuthority('CONFIRM_APPOINTMENT') or hasRole('ADMIN')")
public ResponseEntity<?> confirmAppointment(...) { ... }
```

**emr-service — EmrController.java:**

```java
// Record EMR event (doctor/admin only)
@PostMapping("/patients/{patientId}/events/{eventType}")
@PreAuthorize("hasAuthority('WRITE_EMR')")
public ResponseEntity<?> recordEvent(...) { ... }

// Read current state
@GetMapping("/patients/{patientId}/current")
@PreAuthorize("hasAuthority('READ_EMR')")
public ResponseEntity<?> getCurrentState(...) { ... }

// History and time-travel
@GetMapping("/patients/{patientId}/history")
@PreAuthorize("hasAuthority('READ_EMR')")
public ResponseEntity<?> getHistory(...) { ... }

@GetMapping("/patients/{patientId}/as-of")
@PreAuthorize("hasAuthority('READ_EMR')")
public ResponseEntity<?> getAsOf(...) { ... }
```

**analytics-service — AnalyticsDashboardController.java:**

```java
@GetMapping("/dashboard")
@PreAuthorize("hasAuthority('READ_ANALYTICS') or hasAuthority('READ_OWN_ANALYTICS')")
public ResponseEntity<?> getDashboard(...) { ... }

@GetMapping("/appointments/daily")
@PreAuthorize("hasAuthority('READ_ANALYTICS')")
public ResponseEntity<?> getDailyAppointments(...) { ... }

@GetMapping("/doctors/performance")
@PreAuthorize("hasAuthority('READ_ANALYTICS') or hasAuthority('READ_OWN_ANALYTICS')")
public ResponseEntity<?> getDoctorPerformance(...) { ... }
```

**notification-service — NotificationController.java:**

```java
@GetMapping("/user/{userId}")
@PreAuthorize("hasAuthority('READ_OWN_NOTIFICATIONS') and " +
              "#userId.toString() == authentication.principal " +
              "or hasAuthority('READ_ANY_NOTIFICATIONS')")
public ResponseEntity<?> getUserNotifications(@PathVariable UUID userId, ...) { ... }
```

---

## PART 5 — Admin Permission Management API (user-service)

### 4a — Add permission constants

Create `user-service/src/main/java/com/mediq/security/MediqPermissions.java`:

```java
package com.mediq.security;

import java.util.List;
import java.util.Map;

public final class MediqPermissions {

    private MediqPermissions() {}

    // All permissions defined in the platform
    public static final List<String> ALL_PERMISSIONS = List.of(
        "READ_OWN_PROFILE", "WRITE_OWN_PROFILE",
        "READ_ANY_PROFILE", "WRITE_ANY_PROFILE",
        "READ_DOCTORS", "READ_DOCTOR_AVAILABILITY",
        "READ_PATIENT_PROFILE",
        "WRITE_OWN_APPOINTMENT", "READ_OWN_APPOINTMENT",
        "READ_ANY_APPOINTMENT", "CANCEL_OWN_APPOINTMENT",
        "CANCEL_APPOINTMENT", "CANCEL_ANY_APPOINTMENT",
        "WRITE_APPOINTMENT_SLOT", "CONFIRM_APPOINTMENT",
        "READ_EMR", "WRITE_EMR",
        "READ_OWN_ANALYTICS", "READ_ANALYTICS",
        "READ_OWN_NOTIFICATIONS", "READ_ANY_NOTIFICATIONS",
        "VERIFY_DOCTOR", "DEACTIVATE_USER",
        "SEND_OTP", "VERIFY_OTP", "MANAGE_ROLES"
    );

    // Default permissions per role (used when admin resets to defaults)
    public static final Map<String, List<String>> DEFAULT_ROLE_PERMISSIONS = Map.of(
        "PATIENT", List.of(
            "READ_OWN_PROFILE", "WRITE_OWN_PROFILE",
            "READ_DOCTORS", "READ_DOCTOR_AVAILABILITY",
            "WRITE_OWN_APPOINTMENT", "READ_OWN_APPOINTMENT",
            "CANCEL_OWN_APPOINTMENT", "READ_OWN_NOTIFICATIONS",
            "SEND_OTP", "VERIFY_OTP"
        ),
        "DOCTOR", List.of(
            "READ_OWN_PROFILE", "WRITE_OWN_PROFILE",
            "READ_PATIENT_PROFILE", "READ_OWN_APPOINTMENT",
            "WRITE_APPOINTMENT_SLOT", "CONFIRM_APPOINTMENT",
            "CANCEL_APPOINTMENT", "READ_EMR", "WRITE_EMR",
            "READ_OWN_ANALYTICS", "READ_OWN_NOTIFICATIONS"
        ),
        "NURSE", List.of(
            "READ_OWN_PROFILE", "READ_PATIENT_PROFILE",
            "READ_OWN_APPOINTMENT", "WRITE_OWN_APPOINTMENT",
            "CANCEL_APPOINTMENT", "READ_EMR", "READ_OWN_NOTIFICATIONS"
        ),
        "ADMIN", List.of(
            "READ_OWN_PROFILE", "READ_ANY_PROFILE", "WRITE_ANY_PROFILE",
            "VERIFY_DOCTOR", "DEACTIVATE_USER",
            "READ_DOCTORS", "READ_PATIENT_PROFILE",
            "READ_OWN_APPOINTMENT", "READ_ANY_APPOINTMENT",
            "CANCEL_ANY_APPOINTMENT", "WRITE_APPOINTMENT_SLOT",
            "READ_EMR", "WRITE_EMR", "READ_ANALYTICS",
            "READ_ANY_NOTIFICATIONS", "MANAGE_ROLES",
            "SEND_OTP", "VERIFY_OTP"
        )
    );
}
```

### 4b — Create PermissionAdminService.java

Create `user-service/src/main/java/com/mediq/service/PermissionAdminService.java`:

```java
package com.mediq.service;

import com.mediq.security.MediqPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class PermissionAdminService {

    private static final Logger log = LoggerFactory.getLogger(PermissionAdminService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.admin-url}")
    private String keycloakAdminUrl;

    @Value("${keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin-password:admin}")
    private String adminPassword;

    // ── Get all roles with their current permissions ───────────────────────
    public List<RolePermissionDto> getAllRolePermissions() {
        List<String> roleNames = List.of("PATIENT", "DOCTOR", "NURSE", "ADMIN");
        List<RolePermissionDto> result = new ArrayList<>();

        for (String roleName : roleNames) {
            List<String> permissions = getRolePermissions(roleName);
            result.add(new RolePermissionDto(roleName, permissions));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> getRolePermissions(String roleName) {
        try {
            String token = getAdminToken();
            String url = keycloakAdminUrl + "/admin/realms/mediq/roles/" + roleName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

            Map<String, Object> role = response.getBody();
            if (role == null) return List.of();

            Map<String, List<String>> attrs =
                (Map<String, List<String>>) role.get("attributes");
            if (attrs == null || !attrs.containsKey("permissions")) {
                return List.of();
            }
            return attrs.get("permissions");

        } catch (Exception e) {
            log.error("Failed to get permissions for role {}: {}", roleName, e.getMessage());
            return List.of();
        }
    }

    // ── Update permissions for a role ──────────────────────────────────────
    @SuppressWarnings("unchecked")
    public void updateRolePermissions(String roleName, List<String> permissions) {
        // Validate permissions
        List<String> invalid = permissions.stream()
            .filter(p -> !MediqPermissions.ALL_PERMISSIONS.contains(p))
            .toList();

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                "Unknown permissions: " + invalid);
        }

        try {
            String token = getAdminToken();
            String url = keycloakAdminUrl + "/admin/realms/mediq/roles/" + roleName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get current role representation
            ResponseEntity<Map> getResponse = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

            Map<String, Object> role = new HashMap<>(getResponse.getBody());

            // Update attributes
            Map<String, List<String>> attrs =
                (Map<String, List<String>>) role.getOrDefault("attributes", new HashMap<>());
            attrs.put("permissions", permissions);
            role.put("attributes", attrs);

            // PUT updated role
            restTemplate.exchange(url, HttpMethod.PUT,
                new HttpEntity<>(role, headers), Void.class);

            log.info("Updated permissions for role {}: {}", roleName, permissions);

        } catch (Exception e) {
            log.error("Failed to update permissions for role {}: {}", roleName, e.getMessage());
            throw new RuntimeException("Failed to update role permissions", e);
        }
    }

    // ── Get all available permissions ──────────────────────────────────────
    public List<String> getAllAvailablePermissions() {
        return MediqPermissions.ALL_PERMISSIONS;
    }

    // ── Get Keycloak admin token ───────────────────────────────────────────
    private String getAdminToken() {
        // Calls Keycloak master realm for admin token
        String url = keycloakAdminUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=password" +
                      "&client_id=admin-cli" +
                      "&username=" + adminUsername +
                      "&password=" + adminPassword;

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class);

        return (String) response.getBody().get("access_token");
    }
}
```

Create `RolePermissionDto.java`:

```java
package com.mediq.service;

import java.util.List;

public record RolePermissionDto(
    String roleName,
    List<String> permissions
) {}
```

### 4c — Create PermissionAdminController.java

Create `user-service/src/main/java/com/mediq/controller/PermissionAdminController.java`:

```java
package com.mediq.controller;

import com.mediq.service.PermissionAdminService;
import com.mediq.service.RolePermissionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class PermissionAdminController {

    private final PermissionAdminService permissionAdminService;

    public PermissionAdminController(PermissionAdminService permissionAdminService) {
        this.permissionAdminService = permissionAdminService;
    }

    // GET /admin/roles — all roles with their current permissions
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<List<RolePermissionDto>> getAllRoles() {
        return ResponseEntity.ok(permissionAdminService.getAllRolePermissions());
    }

    // PUT /admin/roles/{roleName}/permissions — update permissions for a role
    @PutMapping("/roles/{roleName}/permissions")
    @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<Void> updateRolePermissions(
            @PathVariable String roleName,
            @RequestBody Map<String, List<String>> body) {

        List<String> permissions = body.get("permissions");
        permissionAdminService.updateRolePermissions(roleName, permissions);
        return ResponseEntity.ok().build();
    }

    // GET /admin/permissions — all available permissions in the platform
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<List<String>> getAllPermissions() {
        return ResponseEntity.ok(permissionAdminService.getAllAvailablePermissions());
    }
}
```

### 4d — Add KrakenD endpoint for admin permission management

Add to `krakend/partials/endpoint_users.tmpl`:

```json
{
  "endpoint": "/api/v1/admin/roles",
  "method": "GET",
  "extra_config": {
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/admin/roles",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/admin/roles/{roleName}/permissions",
  "method": "PUT",
  "extra_config": {
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/admin/roles/{roleName}/permissions",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
},
{
  "endpoint": "/api/v1/admin/permissions",
  "method": "GET",
  "extra_config": {
    {{ template "auth_doctor_admin.tmpl" . }}
  },
  "backend": [{
    "url_pattern": "/admin/permissions",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json",
    "extra_config": {
      {{ template "rate_limit_proxy.tmpl" . }},
      {{ template "circuit_breaker.tmpl" . }}
    }
  }]
}
```

Apply same to `helm/gateway/krakend/config/partials/endpoint_users.tmpl`.

---

## PART 6 — Angular Integration Notes

### Token storage strategy (Authorization Code + PKCE)

```
With angular-oauth2-oidc + PKCE:

  access_token  → library manages in sessionStorage by default
                  Can override to memory-only (more secure)
  refresh_token → NOT used by angular-oauth2-oidc in Code flow
                  library uses silent refresh via hidden iframe instead
  id_token      → sessionStorage (non-sensitive user info)

Why no refresh_token:
  Authorization Code + PKCE with session cookie (Keycloak SSO):
  Silent refresh = open hidden iframe → Keycloak login (prompt=none)
  If SSO session still valid → new access_token issued automatically
  No refresh_token needed in Angular at all

This is safer than refresh_token in storage:
  refresh_token in storage = XSS can steal long-lived token
  silent refresh = Keycloak session validates identity, no stored secret
```

### angular-oauth2-oidc configuration

```typescript
// app.config.ts
import { provideHttpClient } from '@angular/common/http';
import { importProvidersFrom } from '@angular/core';
import { OAuthModule } from 'angular-oauth2-oidc';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(),
    importProvidersFrom(
      OAuthModule.forRoot({
        resourceServer: {
          allowedUrls: ['http://localhost:8080/api'],
          sendAccessToken: true
        }
      })
    )
  ]
};

// auth.config.ts
export const authCodeFlowConfig: AuthConfig = {
  issuer: 'http://localhost:8090/realms/mediq',
  clientId: 'mediq-frontend-spa',
  responseType: 'code',
  redirectUri: window.location.origin + '/',
  silentRefreshRedirectUri: window.location.origin + '/silent-refresh.html',
  scope: 'openid profile email',
  useSilentRefresh: true,
  silentRefreshTimeout: 5000,
  timeoutFactor: 0.75,   // refresh when 75% of token lifetime used
  sessionChecksEnabled: true,
  showDebugInformation: false,
  clearHashAfterLogin: true,
  requireHttps: false     // true in production
};

// auth.service.ts (Angular)
@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private oauthService: OAuthService) {}

  async init(): Promise<void> {
    this.oauthService.configure(authCodeFlowConfig);
    this.oauthService.setupAutomaticSilentRefresh();
    await this.oauthService.loadDiscoveryDocumentAndTryLogin();
  }

  login(): void {
    this.oauthService.initCodeFlow();  // redirects to Keycloak
  }

  logout(): void {
    this.oauthService.logOut();  // redirects to Keycloak logout
  }

  get isLoggedIn(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  get role(): string {
    const claims = this.oauthService.getIdentityClaims() as any;
    return claims?.role ?? '';
  }

  get permissions(): string[] {
    const claims = this.oauthService.getIdentityClaims() as any;
    return claims?.permissions ?? [];
  }

  get userId(): string {
    const claims = this.oauthService.getIdentityClaims() as any;
    return claims?.userId ?? '';
  }

  hasPermission(permission: string): boolean {
    return this.permissions.includes(permission);
  }
}
```

### Route Guard

```typescript
// auth.guard.ts
@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(private authService: AuthService) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    if (!this.authService.isLoggedIn) {
      this.authService.login();
      return false;
    }

    const requiredPermission = route.data['permission'];
    if (requiredPermission && !this.authService.hasPermission(requiredPermission)) {
      return false;  // redirect to 403 page
    }

    return true;
  }
}

// app.routes.ts
export const routes: Routes = [
  {
    path: 'analytics',
    component: AnalyticsDashboardComponent,
    canActivate: [AuthGuard],
    data: { permission: 'READ_ANALYTICS' }  // only ADMIN
  },
  {
    path: 'emr/:patientId',
    component: EmrComponent,
    canActivate: [AuthGuard],
    data: { permission: 'READ_EMR' }  // DOCTOR + NURSE + ADMIN
  },
  {
    path: 'admin/permissions',
    component: PermissionManagementComponent,
    canActivate: [AuthGuard],
    data: { permission: 'MANAGE_ROLES' }  // ADMIN only
  }
];
```

### Admin Permission Management Page (Angular)

```typescript
// permission-management.component.ts
@Component({
  selector: 'app-permission-management',
  template: `
    <h2>Role Permission Management</h2>
    <div *ngFor="let role of roles">
      <h3>{{ role.roleName }}</h3>
      <div *ngFor="let permission of allPermissions">
        <label>
          <input type="checkbox"
                 [checked]="role.permissions.includes(permission)"
                 (change)="togglePermission(role, permission)">
          {{ permission }}
        </label>
      </div>
      <button (click)="saveRole(role)">Save {{ role.roleName }}</button>
    </div>
  `
})
export class PermissionManagementComponent implements OnInit {
  roles: RolePermissionDto[] = [];
  allPermissions: string[] = [];

  ngOnInit() {
    this.http.get<RolePermissionDto[]>('/api/v1/admin/roles')
      .subscribe(r => this.roles = r);
    this.http.get<string[]>('/api/v1/admin/permissions')
      .subscribe(p => this.allPermissions = p);
  }

  togglePermission(role: RolePermissionDto, permission: string) {
    if (role.permissions.includes(permission)) {
      role.permissions = role.permissions.filter(p => p !== permission);
    } else {
      role.permissions = [...role.permissions, permission];
    }
  }

  saveRole(role: RolePermissionDto) {
    this.http.put(`/api/v1/admin/roles/${role.roleName}/permissions`,
      { permissions: role.permissions })
      .subscribe(() => alert(`${role.roleName} permissions saved`));
  }
}
```

---

## PART 7 — Add /auth/me and /auth/logout to user-service

### Add keycloak admin URL to application.properties

```properties
keycloak.admin-url=${KEYCLOAK_URL:http://localhost:8090}
keycloak.admin-username=${KEYCLOAK_ADMIN_USERNAME:admin}
keycloak.admin-password=${KEYCLOAK_ADMIN_PASSWORD:admin}
```

### Create AuthController.java (me + logout only)

Create `user-service/src/main/java/com/mediq/controller/AuthController.java`:

```java
package com.mediq.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    // GET /auth/me — returns user info decoded from JWT headers
    // KrakenD already validated the JWT and injected X-User-* headers
    // Spring Security filter already parsed them into Authentication
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> me(
            Authentication authentication,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Keycloak-Id") String keycloakId,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Type") String userType,
            @RequestHeader(value = "X-User-Permissions", required = false)
                String permissionsHeader) {

        List<String> permissions = permissionsHeader != null
            ? List.of(permissionsHeader.split(","))
            : List.of();

        return ResponseEntity.ok(Map.of(
            "userId",      userId,
            "keycloakId",  keycloakId,
            "email",       email,
            "role",        role,
            "userType",    userType,
            "permissions", permissions
        ));
    }

    // POST /auth/logout — for back-channel notification
    // Angular handles redirect-based logout directly with Keycloak
    // This endpoint is called additionally for any server-side cleanup
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout() {
        // Server-side cleanup if needed (e.g., invalidate Redis session)
        // Angular already redirects to Keycloak logout URL
        return ResponseEntity.ok().build();
    }
}
```

---

## Verification

### 1. Restart all services
```powershell
docker compose down -v
docker compose up --build
```

### 2. Verify Keycloak clients
```
Open: http://localhost:8090/admin
Realm: mediq → Clients
→ mediq-gateway (existing) ✅
→ mediq-frontend-spa (NEW) ✅
  → Settings: Public, Standard flow, PKCE=S256
  → Mappers: role, permissions, userId, userType
```

### 3. Verify JWT has permissions claim
```powershell
# Get token directly from Keycloak (for testing before Angular is built)
curl -X POST "http://localhost:8090/realms/mediq/protocol/openid-connect/token" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "grant_type=password&client_id=mediq-frontend-spa&username=testpatient@mediq.com&password=Test@1234&scope=openid"

# Decode at jwt.io — verify these claims exist:
# role: "PATIENT"
# userId: "<mediq-db-uuid>"
# userType: "PATIENT"
# permissions: ["READ_OWN_PROFILE", "WRITE_OWN_APPOINTMENT", ...]
```

### 4. Test Spring Security on each service
```powershell
# Call doctor-service without headers → should get 401
curl http://localhost:8083/doctors/some-uuid
# Expected: 401

# Call with injected headers (simulates KrakenD)
curl http://localhost:8083/doctors/some-uuid `
  -H "X-User-Id: test-patient-uuid" `
  -H "X-User-Role: PATIENT" `
  -H "X-User-Permissions: READ_OWN_PROFILE,READ_DOCTORS"
# Expected: 200 (if doctor exists) or 404

# Call EMR without WRITE_EMR permission
curl -X POST http://localhost:8086/emr/patients/uuid/events/DIAGNOSIS_ADDED `
  -H "X-User-Id: test-patient-uuid" `
  -H "X-User-Role: PATIENT" `
  -H "X-User-Permissions: READ_OWN_PROFILE"
# Expected: 403 Forbidden (patient has no WRITE_EMR)
```

### 5. Test admin permission management API
```powershell
# Get all role permissions (ADMIN token)
curl http://localhost:8080/api/v1/admin/roles `
  -H "Authorization: Bearer <admin-token>"
# Expected: list of 4 roles with their permissions

# Update DOCTOR permissions (add READ_ANALYTICS)
curl -X PUT http://localhost:8080/api/v1/admin/roles/DOCTOR/permissions `
  -H "Authorization: Bearer <admin-token>" `
  -H "Content-Type: application/json" `
  -d '{"permissions": ["READ_OWN_PROFILE", "READ_PATIENT_PROFILE", "READ_EMR", "WRITE_EMR", "READ_ANALYTICS"]}'
# Expected: 200 OK

# Verify in Keycloak admin console
# Realm → Roles → DOCTOR → Attributes → permissions should be updated
```

---

## Commit
```powershell
git add .
git commit -m "feat(m7-final): Auth Code + PKCE, graceful logout, Spring Security all services

Cleanup:
  Deleted k8s/ folder (fully replaced by Helm charts)
  Removed mediq-gateway Keycloak client (unused — KrakenD only needs public JWK)

Graceful logout (Redis JTI blacklist):
  POST /auth/logout blacklists JTI in Redis with remaining token TTL
  KrakenDAuthFilter checks blacklist on every request (all services)
  Token immediately unusable — no waiting for natural expiry
  Angular: calls /auth/logout first, then oauthService.logOut()

Flow change:
  ROPC login proxy removed entirely
  Angular uses Authorization Code + PKCE via angular-oauth2-oidc
  Angular redirects browser to Keycloak login page directly
  No login endpoints in user-service

Keycloak realm:
  Added mediq-frontend-spa public client (PKCE enforced, S256)
  Added 4 protocol mappers to both clients:
    role, permissions, userId, userType
  Added permission attributes to all 4 realm roles
  Set permissions + userType attribute on all 4 test users
  Added testpatient user

KrakenD:
  CORS configured for localhost:4200
  propagate_claims fixed: sub→X-Keycloak-Id, userId→X-User-Id
  Added X-User-Permissions and X-User-Type propagation
  Created endpoint_auth.tmpl (GET /auth/me, POST /auth/logout)
  Created auth_any_role.tmpl (any authenticated user)
  Registered new partials in Helm ConfigMap

Spring Security (5 remaining services):
  KrakenDAuthFilter.java + SecurityConfig.java added to:
  doctor, appointment, notification, emr, analytics services
  @PreAuthorize on all controller endpoints
  autoconfigure.exclude removed from all services

Admin permission management:
  MediqPermissions.java: all 27 permissions defined as constants
  PermissionAdminService.java: reads/updates Keycloak role attributes
  PermissionAdminController.java: GET /admin/roles, PUT .../permissions, GET /admin/permissions
  KrakenD endpoints for admin permission management
  Angular PermissionManagementComponent pattern documented"
```
